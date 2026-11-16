package teikengstudio.xmpro.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlayPositionTest {
    private fun place() = PlayPosition(
        track = Track(
            rawId = "922867772",
            title = "青山 第2集 李青鸟",
            album = "青山",
            albumId = "108239077",
            coverUrl = "https://imagev2.xmcdn.com/a.jpg",
        ),
        positionSeconds = 73.5,
        at = 1_700_000_000_000L,
    )

    @Test
    fun `a place survives a restart`() {
        val restored = PlayPosition.fromJson(place().toJson())
        assertEquals("922867772", restored?.track?.number)
        assertEquals("108239077", restored?.albumKey)
        assertEquals("青山 第2集 李青鸟", restored?.track?.title)
        assertEquals(73.5, restored?.positionSeconds)
        assertEquals(1_700_000_000_000L, restored?.at)
    }

    @Test
    fun `a row without a number is dropped instead of resuming nothing`() {
        assertNull(PlayPosition.fromJson(org.json.JSONObject().put("title", "青山")))
    }

    @Test
    fun `a missing cover is not stored as an empty url`() {
        val bare = PlayPosition(Track(rawId = "1", title = "t", albumId = "2"), 1.0, 5L)
        val restored = PlayPosition.fromJson(bare.toJson())
        assertNull(restored?.track?.coverUrl)
        assertTrue(bare.toJson().getString("cover_url").isEmpty())
    }
}
