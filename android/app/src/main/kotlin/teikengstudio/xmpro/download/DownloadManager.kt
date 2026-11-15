package teikengstudio.xmpro.download

import android.content.Context
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import teikengstudio.xmpro.data.AppStorage
import teikengstudio.xmpro.model.ResolvedAudio
import teikengstudio.xmpro.model.Track
import teikengstudio.xmpro.model.XmlyException

/**
 * Offline episode queue.
 *
 * Files land in the app's private external directory, so they disappear with an
 * uninstall; the task list is persisted in `state.json` so a restart keeps
 * paused and failed rows visible.
 *
 * Downloads run one at a time — a phone on a metered link handles a serial queue
 * better than three parallel connections, and the progress the user sees is
 * honest about what is happening.
 */
class DownloadManager(
    context: Context,
    private val store: AppStorage,
    /** Where finished files live; created on demand. */
    private val directory: () -> File,
    /** Fresh playable URL for one episode at the current quality setting. */
    private val resolve: suspend (Track, String) -> ResolvedAudio,
    private val quality: () -> String = { "auto" },
    /** True while the queue is working, so the caller can hold a service. */
    private val onRunning: (Boolean) -> Unit = {},
) {
    private val appContext = context.applicationContext
    private val http = OkHttpClient()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _tasks = MutableStateFlow(load())
    val tasks: StateFlow<List<DownloadTask>> = _tasks.asStateFlow()

    private var pump: Job? = null
    private var current: Job? = null

    init {
        // A restored backup or a cleared cache leaves rows pointing at files
        // that no longer exist; drop them before the user sees them.
        pruneMissing()
    }

    // ------------------------------------------------------------- public API

    /**
     * Adds [tracks] to the queue, skipping what is already there.
     *
     * [quality] overrides the global setting for these episodes only, which is
     * how "download this one in HD" works without touching playback.
     */
    fun enqueue(tracks: List<Track>, quality: String? = null) {
        if (tracks.isEmpty()) return
        val known = _tasks.value.map { it.id }.toSet()
        val added = tracks.filterNot { it.id in known }.map { track ->
            DownloadTask(
                track = track,
                path = fileFor(track).absolutePath,
                requestQuality = quality,
            )
        }
        if (added.isEmpty()) return
        _tasks.update { it + added }
        persist()
        start()
    }

    fun pause(id: String) {
        val task = _tasks.value.firstOrNull { it.id == id } ?: return
        if (task.state == DownloadState.DONE) return
        if (current != null && task.state == DownloadState.RUNNING) {
            // The loop stops after the current write and re-reads the state.
            current?.cancel()
        }
        update(id) { it.copy(state = DownloadState.PAUSED) }
    }

    fun resume(id: String) {
        val task = _tasks.value.firstOrNull { it.id == id } ?: return
        if (task.state == DownloadState.DONE || task.state == DownloadState.RUNNING) return
        update(id) { it.copy(state = DownloadState.QUEUED, error = null) }
        start()
    }

    /** Removes the row and its file; the queue moves on. */
    fun remove(id: String) {
        val task = _tasks.value.firstOrNull { it.id == id } ?: return
        if (task.state == DownloadState.RUNNING) current?.cancel()
        runCatching { File(task.path).delete() }
        _tasks.update { list -> list.filterNot { it.id == id } }
        persist()
    }

    fun retry(id: String) = resume(id)

    /** Deletes every finished episode; paused or failed rows are kept. */
    fun clearFinished() {
        val finished = _tasks.value.filter { it.state == DownloadState.DONE }
        finished.forEach { runCatching { File(it.path).delete() } }
        _tasks.update { list -> list.filterNot { it.state == DownloadState.DONE } }
        persist()
    }

    /** Deletes everything, files included. */
    fun clearAll() {
        current?.cancel()
        _tasks.value.forEach { runCatching { File(it.path).delete() } }
        _tasks.update { emptyList() }
        persist()
    }

    /** Local file for [trackId], or null when the episode is not downloaded. */
    fun localFile(trackId: String): File? {
        val task = _tasks.value.firstOrNull { it.id == trackId } ?: return null
        if (task.state != DownloadState.DONE) return null
        return File(task.path).takeIf { it.isFile && it.length() > 0 }
    }

    fun hasLocal(trackId: String): Boolean = localFile(trackId) != null

    fun totalBytes(): Long =
        _tasks.value.filter { it.state == DownloadState.DONE }.sumOf { File(it.path).length() }

    /** Drops rows whose file vanished (cleared cache, restored backup). */
    fun pruneMissing() {
        val gone = _tasks.value.filter { it.state == DownloadState.DONE && !File(it.path).isFile }
        if (gone.isEmpty()) return
        _tasks.update { list -> list.filterNot { task -> gone.any { it.id == task.id } } }
        persist()
    }

    // ---------------------------------------------------------------- engine

    private fun start() {
        if (pump?.isActive == true) return
        onRunning(true)
        DownloadService.start(appContext)
        pump = scope.launch {
            try {
                while (true) {
                    val next = _tasks.value.firstOrNull { it.state == DownloadState.QUEUED } ?: break
                    val job = launch { download(next) }
                    current = job
                    job.join()
                    current = null
                }
            } finally {
                pump = null
                onRunning(false)
                DownloadService.stop(appContext)
                persist()
            }
        }
    }

    private suspend fun download(task: DownloadTask) {
        update(task.id) { it.copy(state = DownloadState.RUNNING, error = null) }
        persist()
        var attempt = 0
        while (true) {
            // A paused or deleted row must not be written to any more.
            if (_tasks.value.firstOrNull { it.id == task.id }?.state != DownloadState.RUNNING) return
            val result = runCatching { fetch(task) }
            if (result.isSuccess) {
                val path = result.getOrThrow()
                update(task.id) {
                    val length = File(path).length()
                    it.copy(state = DownloadState.DONE, received = length, total = length, error = null)
                }
                persist()
                return
            }
            val error = result.exceptionOrNull()
            if (error is kotlinx.coroutines.CancellationException) return
            // Signed CDN links expire; a stale URL is the usual cause of a 403
            // or 404, so re-resolve once before giving up.
            val stale = (error as? IOException)?.message?.contains("HTTP 4") == true
            if (stale && attempt < 2) {
                attempt++
                continue
            }
            update(task.id) { it.copy(state = DownloadState.FAILED, error = describe(error)) }
            persist()
            return
        }
    }

    /** Streams one episode to disk and answers with the finished path. */
    private suspend fun fetch(task: DownloadTask): String = withContext(Dispatchers.IO) {
        val resolved = resolve(task.track, task.requestQuality ?: quality())
        // The container only becomes known from the URL, and the player shows
        // the file's name in logs; keep the extension honest.
        val file = fileFor(task.track, resolved.url)
        file.parentFile?.mkdirs()
        if (file.absolutePath != task.path) update(task.id) { it.copy(path = file.absolutePath) }
        val have = if (file.isFile) file.length() else 0L
        val request = Request.Builder()
            .url(resolved.url)
            .apply {
                resolved.headers.forEach { (key, value) -> header(key, value) }
                if (have > 0) header("Range", "bytes=$have-")
            }
            .build()
        http.newCall(request).execute().use { response ->
            if (response.code == 416) return@use file.absolutePath
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val body = response.body ?: throw IOException("响应体为空")
            val length = body.contentLength()
            val total = if (length > 0) length + have else 0L
            if (total > 0) update(task.id) { it.copy(total = total, quality = resolved.actualQuality) }
            body.byteStream().use { input ->
                file.outputStream().use { output ->
                    // Appending keeps a resumed download's earlier bytes.
                    if (have > 0) output.channel.position(have)
                    val buffer = ByteArray(64 * 1024)
                    var written = have
                    var lastPublish = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        if (_tasks.value.firstOrNull { it.id == task.id }?.state != DownloadState.RUNNING) break
                        output.write(buffer, 0, read)
                        written += read
                        val now = System.currentTimeMillis()
                        if (now - lastPublish > 400) {
                            lastPublish = now
                            update(task.id) { it.copy(received = written) }
                        }
                    }
                    output.flush()
                }
            }
        }
        file.absolutePath
    }

    // ------------------------------------------------------------- internals

    private fun fileFor(track: Track, url: String? = null): File {
        val directory = directory().apply { mkdirs() }
        val fromUrl = url
            ?.substringBefore('?')
            ?.substringAfterLast('.', "")
            ?.lowercase()
            ?.takeIf { it in AUDIO_EXTENSIONS }
        val extension = fromUrl
            ?: track.raw["ext"]?.toString()?.takeIf { it in AUDIO_EXTENSIONS }
            ?: "m4a"
        return File(directory, "${track.number}.$extension")
    }

    private fun update(id: String, change: (DownloadTask) -> DownloadTask) {
        _tasks.update { list -> list.map { if (it.id == id) change(it) else it } }
    }

    private fun load(): List<DownloadTask> {
        val array = store.state().optJSONArray("downloads") ?: return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            array.optJSONObject(index)?.let(DownloadTask::fromJson)
        }
    }

    private fun persist() {
        runCatching {
            val array = JSONArray()
            _tasks.value.forEach { array.put(it.toJson()) }
            store.transaction { state -> state.put("downloads", array) }
        }
    }

    companion object {
        /** Containers the service actually serves; anything else is ignored. */
        private val AUDIO_EXTENSIONS = setOf("mp3", "m4a", "aac", "flac")
    }

    private fun describe(error: Throwable?): String = when (error) {
        null -> "下载失败"
        is XmlyException -> error.message
        else -> error.message ?: "下载失败"
    }
}
