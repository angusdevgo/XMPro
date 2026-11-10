package teikengstudio.xmpro.api

import java.time.Instant
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import teikengstudio.xmpro.model.Credentials
import teikengstudio.xmpro.model.XmlyException

class XmlyClientTest {
    private val server = MockWebServer().apply { start() }
    private val clock: () -> Instant = { Instant.parse("2026-11-10T00:00:00Z") }
    private val credentials = Credentials("123456&abcdef", "dev-1")

    private fun base(): String = server.url("/").toString().removeSuffix("/")

    private fun client(withAccount: Boolean = false, searchOrigin: String = XmlyHttp.SEARCH_ORIGIN) = XmlyClient(
        http = XmlyHttp(clock = clock, searchOrigin = searchOrigin),
        credentials = if (withAccount) credentials else null,
        clock = clock,
        mobile = base(),
        searchOrigin = searchOrigin,
    )

    @AfterTest
    fun stop() {
        server.shutdown()
    }

    private fun albumPage(
        total: Int = 3,
        pageSize: Int = 3,
        list: String = """[
            {"trackId":101,"title":"第一集","duration":301,"orderNo":1,"albumId":998,"isPaid":true},
            {"trackId":102,"title":"第二集","duration":302,"orderNo":2,"albumId":998},
            {"trackId":103,"title":"第三集","duration":303,"orderNo":3,"albumId":998}
        ]""",
    ): String = """
        {"ret":0,"album":{"albumId":998,"albumTitle":"测试专辑","nickname":"主播"},
         "data":{"albumId":998,"pageId":1,"pageSize":$pageSize,"totalCount":$total,"list":$list}}
    """.trimIndent()

    @Test
    fun `album parses tracks and keeps official order`() {
        server.enqueue(MockResponse().setBody(albumPage()))
        val page = client().album("998", pageSize = 3)
        assertEquals(998, page.album.id.toInt())
        assertEquals("测试专辑", page.album.title)
        assertEquals(3, page.total)
        assertEquals(listOf("101", "102", "103"), page.tracks.map { it.number })
        assertEquals(listOf(1, 2, 3), page.tracks.map { it.raw["officialPosition"] })
        assertEquals(301.0, page.tracks.first().duration, 0.001)
        assertEquals(true, page.tracks.first().isPaid)
    }

    @Test
    fun `album rejects duplicates wrong ids and short pages`() {
        val duplicate = """[{"trackId":101,"orderNo":1,"albumId":998},{"trackId":101,"orderNo":2,"albumId":998}]"""
        server.enqueue(MockResponse().setBody(albumPage(total = 2, pageSize = 2, list = duplicate)))
        assertEquals(
            "duplicate_page",
            assertFailsWith<XmlyException> { client().album("998", pageSize = 2) }.code,
        )

        val foreign = """[{"trackId":101,"orderNo":1,"albumId":999}]"""
        server.enqueue(MockResponse().setBody(albumPage(total = 1, pageSize = 1, list = foreign)))
        assertEquals(
            "id_mismatch",
            assertFailsWith<XmlyException> { client().album("998", pageSize = 1) }.code,
        )

        server.enqueue(MockResponse().setBody(albumPage(total = 5, pageSize = 5, list = """[{"trackId":101,"orderNo":1,"albumId":998}]""")))
        assertEquals(
            "incomplete_catalogue",
            assertFailsWith<XmlyException> { client().album("998", pageSize = 5) }.code,
        )

        server.enqueue(MockResponse().setBody(albumPage()))
        assertEquals(
            "id_mismatch",
            assertFailsWith<XmlyException> { client().album("997", pageSize = 3) }.code,
        )
    }

    @Test
    fun `album rejects conflicting order numbers`() {
        val list = """[
            {"trackId":101,"orderNo":2,"albumId":998},
            {"trackId":102,"orderNo":1,"albumId":998}
        ]"""
        server.enqueue(MockResponse().setBody(albumPage(total = 2, pageSize = 2, list = list)))
        assertEquals(
            "invalid_catalogue",
            assertFailsWith<XmlyException> { client().album("998", pageSize = 2) }.code,
        )
    }

    @Test
    fun `search maps docs into tracks and albums`() {
        server.enqueue(MockResponse().setBody("""
            {"ret":200,"data":{"result":{"response":{"numFound":2,"docs":[
              {"trackId":201,"title":"声音","duration":100,"albumId":998,"albumTitle":"专辑"},
              {"albumId":777,"albumTitle":"另一个专辑","nickname":"主播"}
            ]}}}}
        """.trimIndent()))
        val tracks = client(searchOrigin = base()).search("关键词", "track")
        assertEquals(1, tracks.tracks.size)
        assertEquals("201", tracks.tracks.single().number)
        assertEquals(2, tracks.total)

        server.enqueue(MockResponse().setBody("""
            {"ret":200,"data":{"result":{"response":{"numFound":1,"docs":[
              {"albumId":777,"albumTitle":"专辑X","nickname":"主播"}
            ]}}}}
        """.trimIndent()))
        val albums = client(searchOrigin = base()).search("关键词", "album")
        assertEquals(1, albums.albums.size)
        assertEquals("777", albums.albums.single().id)
    }


    @Test
    fun `resolve prefers the highest available quality`() {
        val url64 = XmlyCrypto.encrypt("https://audio.xmcdn.com/a64.m4a", XmlyCrypto.playKey).replace("\n", "")
        val url32 = XmlyCrypto.encrypt("https://audio.xmcdn.com/a32.mp3", XmlyCrypto.playKey).replace("\n", "")
        server.enqueue(MockResponse().setBody("""
            {"ret":0,"data":{"trackId":101,"title":"第一集","duration":301,
             "isPaid":false,"playUrl64":"$url64","playUrl32":"$url32"}}
        """.trimIndent()))
        val audio = client().resolve("101")
        assertEquals("mp3_64", audio.actualQuality)
        assertEquals("https://audio.xmcdn.com/a64.m4a", audio.url)
        assertEquals(false, audio.requiresReward)
        assertEquals("101", audio.track.number)
    }

    @Test
    fun `resolve honours an explicit quality and rejects unknown ones`() {
        val url32 = XmlyCrypto.encrypt("https://audio.xmcdn.com/a32.mp3", XmlyCrypto.playKey).replace("\n", "")
        server.enqueue(MockResponse().setBody("""
            {"ret":0,"data":{"trackId":101,"duration":301,"isPaid":false,"playUrl32":"$url32"}}
        """.trimIndent()))
        assertEquals("mp3_32", client().resolve("101", quality = "mp3_32").actualQuality)
        assertEquals(
            "invalid_quality",
            assertFailsWith<XmlyException> { client().resolve("101", quality = "wav") }.code,
        )
    }

    @Test
    fun `resolve requires listening time for paid tracks`() {
        server.enqueue(MockResponse().setBody("""{"ret":0,"data":{"trackId":101,"duration":301,"isPaid":true}}"""))
        assertEquals(
            "login_required",
            assertFailsWith<XmlyException> { client().resolve("101") }.code,
        )

        // With an account: the quota probe reports zero remaining seconds.
        server.enqueue(MockResponse().setBody(encryptedInfo("""{"trackId":101,"duration":301,"isPaid":true}""")))
        server.enqueue(MockResponse().setBody(quotaBody("0", "false")))
        assertEquals(
            "insufficient_quota",
            assertFailsWith<XmlyException> { client(withAccount = true).resolve("101") }.code,
        )
    }

    @Test
    fun `resolve rejects trial only payloads`() {
        server.enqueue(MockResponse().setBody("""
            {"ret":0,"data":{"trackId":101,"duration":301,"isPaid":false,"isPreview":true,
             "playUrl64":"https://audio.xmcdn.com/a.m4a"}}
        """.trimIndent()))
        assertEquals(
            "preview_only",
            assertFailsWith<XmlyException> { client().resolve("101") }.code,
        )
    }

    @Test
    fun `resolve rejects payloads without any playable url`() {
        server.enqueue(MockResponse().setBody("""{"ret":0,"data":{"trackId":101,"duration":301,"isPaid":false}}"""))
        assertEquals(
            "permission_denied",
            assertFailsWith<XmlyException> { client().resolve("101") }.code,
        )
    }


    /** Wraps track info in the encrypted envelope used by signed baseInfo. */
    private fun encryptedInfo(info: String): String {
        val inner = "{\"trackInfo\":$info}"
        val cipher = XmlyCrypto.encrypt(inner, XmlyCrypto.playKey).replace("\n", "")
        return """{"ret":0,"data":"$cipher"}"""
    }

    private fun quotaBody(seconds: String, newDevice: String, stamp: Long = clock().toEpochMilli()): String {
        val duration = XmlyCrypto.encrypt("$seconds|$stamp", XmlyCrypto.durationKey).replace("\n", "")
        val device = XmlyCrypto.encrypt("$newDevice|$stamp", XmlyCrypto.durationKey).replace("\n", "")
        return """{"ret":0,"data":{"availableListenDuration":"$duration","newDevice":"$device"}}"""
    }

    @Test
    fun `quota decodes the encrypted balance and device flag`() {
        server.enqueue(MockResponse().setBody(quotaBody("3600", "false")))
        val quota = client(withAccount = true).quota("account-1")
        assertEquals(3600, quota.remainingSeconds)
        assertEquals(false, quota.newDevice)

        val request = server.takeRequest()
        val body = request.body.readUtf8()
        // The lite client posts this endpoint as JSON; a form body is rejected
        // with ret=-100 even when the signature is correct.
        assertTrue(body.contains("\"signature\""), "signature is submitted")
        assertTrue(body.contains("\"deviceId\":\"dev-1\""))
        assertTrue(!body.contains("\"uid\""), "uid is never sent back")
        assertTrue(request.getHeader("Content-Type")!!.startsWith("application/json"))
        assertEquals(credentials.cookie, request.getHeader("Cookie"))
    }

    @Test
    fun `quota rejects stale or malformed payloads`() {
        server.enqueue(MockResponse().setBody(quotaBody("3600", "false", stamp = clock().toEpochMilli() - 600_000)))
        assertEquals(
            "invalid_quota",
            assertFailsWith<XmlyException> { client(withAccount = true).quota("account-1") }.code,
        )

        server.enqueue(MockResponse().setBody("""{"ret":0,"data":{"availableListenDuration":"not-encrypted","newDevice":"x"}}"""))
        assertEquals(
            "invalid_quota",
            assertFailsWith<XmlyException> { client(withAccount = true).quota("account-1") }.code,
        )
    }

    @Test
    fun `debit sends the requested duration`() {
        server.enqueue(MockResponse().setBody(quotaBody("3540", "false")))
        val quota = client(withAccount = true).debit("account-1", 60, "op-1")
        assertEquals(3540, quota.remainingSeconds)
        assertTrue(server.takeRequest().body.readUtf8().contains("\"duration\":\"60\""))
    }

    @Test
    fun `track info verifies the returned identity`() {
        server.enqueue(MockResponse().setBody(encryptedInfo("""{"trackId":101,"title":"第一集","duration":301,"albumId":998}""")))
        val track = client(withAccount = true).track("101")
        assertEquals("101", track.number)
        assertEquals(301.0, track.duration, 0.001)

        // Anonymous callers use the public endpoint with plain JSON.
        server.enqueue(MockResponse().setBody("""{"ret":0,"data":{"trackId":101,"title":"公开","duration":12}}"""))
        assertEquals("公开", client().track("101").title)

        server.enqueue(MockResponse().setBody(encryptedInfo("""{"trackId":999,"title":"别的"}""")))
        assertEquals(
            "id_mismatch",
            assertFailsWith<XmlyException> { client(withAccount = true).track("101") }.code,
        )
    }
}
