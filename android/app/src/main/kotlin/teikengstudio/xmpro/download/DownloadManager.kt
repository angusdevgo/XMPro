package teikengstudio.xmpro.download

import android.content.Context
import android.content.Intent
import java.io.File
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
import org.json.JSONObject
import teikengstudio.xmpro.data.AppStorage
import teikengstudio.xmpro.model.Track
import teikengstudio.xmpro.model.XmlyException

enum class DownloadState { queued, downloading, paused, completed, failed }

data class DownloadTask(
    val id: String,
    val number: String,
    val title: String,
    val quality: String? = null,
    val path: String,
    val state: DownloadState = DownloadState.queued,
    val receivedBytes: Long = 0,
    val totalBytes: Long = 0,
    val error: String? = null,
) {
    val progress: Float
        get() = if (totalBytes > 0) (receivedBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f
}

/**
 * Sequential download queue with durable task state.
 *
 * Files land under the app's external downloads directory; the queue itself is
 * persisted in `state.json` so a restart keeps paused/failed tasks visible.
 */
class DownloadManager(
    private val context: Context,
    private val storage: AppStorage,
    private val directory: () -> File,
    /** Resolves a playable URL for a track number; also used on resume. */
    private val resolve: suspend (String) -> Pair<String, String>,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val http = OkHttpClient()
    private val _tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val tasks: StateFlow<List<DownloadTask>> = _tasks.asStateFlow()

    private var worker: Job? = null
    private var current: Job? = null

    init {
        load()
    }

    private fun load() {
        val saved = storage.state().optJSONArray("downloadQueue") ?: JSONArray()
        val restored = (0 until saved.length()).mapNotNull { index ->
            val item = saved.optJSONObject(index) ?: return@mapNotNull null
            DownloadTask(
                id = item.optString("id"),
                number = item.optString("number"),
                title = item.optString("title"),
                quality = item.optString("quality").takeIf { it.isNotEmpty() },
                path = item.optString("path"),
                state = when (item.optString("state")) {
                    "completed" -> DownloadState.completed
                    "failed" -> DownloadState.failed
                    "paused" -> DownloadState.paused
                    else -> DownloadState.paused
                },
                receivedBytes = item.optLong("received"),
                totalBytes = item.optLong("total"),
                error = item.optString("error").takeIf { it.isNotEmpty() },
            )
        }.filter { it.id.isNotEmpty() && it.path.isNotEmpty() }
        _tasks.value = restored
    }

    private fun persist() {
        runCatching {
            storage.transaction { state ->
                state.put(
                    "downloadQueue",
                    JSONArray().apply {
                        for (task in _tasks.value) {
                            put(
                                JSONObject().apply {
                                    put("id", task.id)
                                    put("number", task.number)
                                    put("title", task.title)
                                    put("quality", task.quality ?: "")
                                    put("path", task.path)
                                    put("state", task.state.name)
                                    put("received", task.receivedBytes)
                                    put("total", task.totalBytes)
                                    put("error", task.error ?: "")
                                },
                            )
                        }
                    },
                )
            }
        }
    }

    /** Adds a task; the URL is resolved lazily when the worker reaches it. */
    fun enqueue(track: Track): DownloadTask {
        val existing = _tasks.value.firstOrNull { it.number == track.number }
        if (existing != null && existing.state != DownloadState.failed) return existing
        val id = "${track.number}-${System.currentTimeMillis()}"
        val target = File(directory(), fileName(track.title, track.number, "m4a"))
        val task = DownloadTask(id = id, number = track.number, title = track.title, path = target.absolutePath)
        _tasks.update { it + task }
        persist()
        launchWorker()
        return task
    }

    fun pause(id: String) {
        _tasks.update { list -> list.map { if (it.id == id) it.copy(state = DownloadState.paused) else it } }
        if (_tasks.value.none { it.state == DownloadState.downloading }) current?.cancel()
        persist()
        stopServiceIfIdle()
    }

    fun resume(id: String) {
        _tasks.update { list ->
            list.map { if (it.id == id && it.state != DownloadState.completed) it.copy(state = DownloadState.queued) else it }
        }
        persist()
        launchWorker()
    }

    fun cancel(id: String) {
        val task = _tasks.value.firstOrNull { it.id == id } ?: return
        if (task.state == DownloadState.downloading) current?.cancel()
        runCatching { File(task.path).delete() }
        _tasks.update { list -> list.filterNot { it.id == id } }
        persist()
        stopServiceIfIdle()
    }

    fun clearCompleted() {
        val completed = _tasks.value.filter { it.state == DownloadState.completed }
        completed.forEach { runCatching { File(it.path).delete() } }
        _tasks.update { list -> list.filterNot { it.state == DownloadState.completed } }
        persist()
    }

    private fun launchWorker() {
        if (worker?.isActive == true) return
        worker = scope.launch {
            while (true) {
                val next = _tasks.value.firstOrNull { it.state == DownloadState.queued } ?: break
                startForeground()
                current = launch { run(next) }
                current?.join()
            }
            stopServiceIfIdle()
        }
    }

    private suspend fun run(task: DownloadTask) {
        update(task.id) { it.copy(state = DownloadState.downloading, error = null) }
        try {
            val target = File(task.path)
            val (stream, quality) = resolve(task.number)
            update(task.id) { it.copy(quality = quality) }
            withContext(Dispatchers.IO) {
                val request = Request.Builder().url(stream).build()
                http.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw XmlyException("http", "下载失败 HTTP ${response.code}")
                    val body = response.body ?: throw XmlyException("http", "下载响应为空")
                    val total = body.contentLength()
                    body.byteStream().use { input ->
                        target.outputStream().use { output ->
                            val buffer = ByteArray(64 * 1024)
                            var copied = 0L
                            while (true) {
                                val read = input.read(buffer)
                                if (read <= 0) break
                                output.write(buffer, 0, read)
                                copied += read
                                update(task.id) {
                                    it.copy(receivedBytes = copied, totalBytes = if (total > 0) total else 0)
                                }
                            }
                        }
                    }
                }
            }
            update(task.id) { it.copy(state = DownloadState.completed, error = null) }
        } catch (error: Exception) {
            val cancelled = _tasks.value.firstOrNull { it.id == task.id }?.state == DownloadState.paused
            update(task.id) {
                if (cancelled) it.copy(state = DownloadState.paused)
                else it.copy(state = DownloadState.failed, error = error.message ?: "下载失败")
            }
        }
    }

    private fun update(id: String, change: (DownloadTask) -> DownloadTask) {
        _tasks.update { list -> list.map { if (it.id == id) change(it) else it } }
        persist()
        DownloadService.updateNotification(context, _tasks.value)
    }

    private fun startForeground() {
        runCatching {
            context.startForegroundService(Intent(context, DownloadService::class.java))
        }
    }

    private fun stopServiceIfIdle() {
        if (_tasks.value.any { it.state == DownloadState.downloading || it.state == DownloadState.queued }) return
        runCatching { context.stopService(Intent(context, DownloadService::class.java)) }
    }

    private fun fileName(title: String, number: String, extension: String): String {
        val safe = title.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(80).ifEmpty { "audio" }
        return "$safe [$number].$extension"
    }
}
