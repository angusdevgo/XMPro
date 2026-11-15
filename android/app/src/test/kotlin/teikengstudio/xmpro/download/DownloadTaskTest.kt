package teikengstudio.xmpro.download

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.json.JSONObject
import teikengstudio.xmpro.model.Track

/**
 * The queue lives in `state.json`, so a task has to survive a write/read cycle
 * with everything the row needs — otherwise a restart shows blank titles.
 */
class DownloadTaskTest {

    private fun task() = DownloadTask(
        track = Track(
            rawId = "732916741",
            title = "第1集 归零",
            artist = "头陀渊",
            album = "青山",
            albumId = "601293872",
            duration = 399.0,
            coverUrl = "https://img.example/cover.jpg",
        ),
        path = "/storage/emulated/0/Android/data/app/files/xmpro/audio/732916741.m4a",
        quality = "mp3_64",
        received = 1024,
        total = 2048,
        state = DownloadState.PAUSED,
    )

    @Test
    fun `a queued task survives a state file round trip`() {
        val restored = DownloadTask.fromJson(task().toJson())
        assertEquals(task().id, restored?.id)
        assertEquals("第1集 归零", restored?.track?.title)
        assertEquals("601293872", restored?.track?.albumKey)
        assertEquals(DownloadState.PAUSED, restored?.state)
        assertEquals(1024, restored?.received)
        assertEquals(0.5f, restored?.progress)
    }

    @Test
    fun `rows without a path or an episode number are dropped`() {
        assertNull(DownloadTask.fromJson(JSONObject("""{"track":{"number":"1"}}""")))
        assertNull(DownloadTask.fromJson(JSONObject("""{"path":"/tmp/a.m4a","track":{}}""")))
    }

    @Test
    fun `a finished task reports full progress even before sizes arrive`() {
        val done = DownloadTask(
            track = Track(rawId = "1"),
            path = "/tmp/a.m4a",
            state = DownloadState.DONE,
        )
        assertEquals(1f, done.progress)
    }

    @Test
    fun `an unknown state string falls back to queued`() {
        assertEquals(DownloadState.QUEUED, DownloadState.of("SOMETHING_NEW"))
        assertEquals(DownloadState.FAILED, DownloadState.of("FAILED"))
    }
}
