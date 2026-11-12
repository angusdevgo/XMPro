package teikengstudio.xmpro.api

import java.time.Instant
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer

/**
 * The collector origin only answers with the lite app agent, and the POST
 * helpers used to drop every header, which silently broke history sync.
 */
class PostHeadersTest {
    private val server = MockWebServer().apply { start() }
    private val clock: () -> Instant = { Instant.parse("2026-11-10T00:00:00Z") }
    private val origin: String = server.url("/").toString().removeSuffix("/")

    @AfterTest
    fun stop() {
        server.shutdown()
    }

    @Test
    fun `only the collector origin asks for the app agent`() {
        assertTrue(XmlyClient.originHeaders(XmlyClient.COLLECTOR).containsKey("User-Agent"))
        assertEquals("ting_3.4.10.3(Android,30)", XmlyClient.originHeaders(XmlyClient.COLLECTOR)["User-Agent"])
        assertTrue(XmlyClient.originHeaders(XmlyClient.MOBILE).isEmpty())
    }

    @Test
    fun `postForm forwards the caller headers`() {
        server.enqueue(MockResponse().setBody("""{"ret":0,"data":{"syncPoint":1}}"""))
        val http = XmlyHttp(clock = clock)
        http.postForm(
            origin = origin,
            path = "/nyx/v1/history/merge/android",
            form = mapOf("mergeRecords" to "[]"),
            headers = XmlyClient.originHeaders(XmlyClient.COLLECTOR),
        )
        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("ting_3.4.10.3(Android,30)", recorded.getHeader("User-Agent"))
    }

    @Test
    fun `postJson forwards the caller headers`() {
        server.enqueue(MockResponse().setBody("""{"ret":0}"""))
        val http = XmlyHttp(clock = clock)
        http.postJson(
            origin = origin,
            path = "/whatever",
            json = mapOf("a" to 1),
            headers = mapOf("Accept" to "*/*"),
        )
        assertEquals("*/*", server.takeRequest().getHeader("Accept"))
    }
}
