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

class XmlyHttpTest {
    private val server = MockWebServer().apply { start() }
    private val clock: () -> Instant = { Instant.parse("2026-11-10T00:00:00Z") }
    private val http = XmlyHttp(clock = clock)

    @AfterTest
    fun stop() {
        server.shutdown()
    }

    private fun base(): String = server.url("/").toString().removeSuffix("/")

    @Test
    fun `returns the decoded payload for ret 0`() {
        server.enqueue(MockResponse().setBody("""{"ret":0,"data":{"ok":true}}"""))
        val payload = http.get(base(), "/fmobile-track/fmobile/track/v2/baseInfo")
        assertEquals(0, payload.getInt("ret"))
        assertTrue(payload.getJSONObject("data").getBoolean("ok"))
    }

    @Test
    fun `search origin expects ret 200 and maps other codes to rejected`() {
        val searchHttp = XmlyHttp(clock = clock, searchOrigin = base())
        server.enqueue(MockResponse().setBody("""{"ret":200,"data":{"result":{}}}"""))
        assertEquals(200, searchHttp.get(base(), "/revision/search").getInt("ret"))

        server.enqueue(MockResponse().setBody("""{"ret":0}"""))
        val failure = assertFailsWith<XmlyException> {
            searchHttp.get(base(), "/revision/search")
        }
        assertEquals("rejected", failure.code)
    }

    @Test
    fun `sends the account cookie and a signed ticket header`() {
        server.enqueue(MockResponse().setBody("""{"ret":0}"""))
        val credentials = Credentials("123456&abcdef", "dev-1")
        http.get(base(), "/fmobile-track/fmobile/track/v2/baseInfo", credentials = credentials, signed = true)
        val recorded = server.takeRequest()
        assertEquals(credentials.cookie, recorded.getHeader("Cookie"))
        assertTrue(recorded.getHeader("x-tk")!!.startsWith("TAC"))
    }

    @Test
    fun `expired login is definitive`() {
        server.enqueue(MockResponse().setResponseCode(401))
        val failure = assertFailsWith<XmlyException> {
            http.get(base(), "/fmobile-track/fmobile/track/v2/baseInfo", credentials = Credentials("123456&abcdef", "dev"))
        }
        assertEquals("login_expired", failure.code)
        assertTrue(failure.definitive)
    }

    @Test
    fun `429 keeps the server cooldown instead of retrying immediately`() {
        server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "5"))
        val failure = assertFailsWith<XmlyException> {
            http.get(base(), "/fmobile-track/fmobile/track/v2/baseInfo", readOnly = false)
        }
        assertEquals("rate_limited", failure.code)
        assertEquals(5, failure.retryAfter?.seconds)
    }

    @Test
    fun `declared and streamed bodies respect the response cap`() {
        server.enqueue(MockResponse().setBody("""{"ret":0,"pad":"${"x".repeat(200)}"}"""))
        val tooBig = assertFailsWith<XmlyException> {
            http.postJson(base(), "/lite-mobile/like/v1/like", responseLimit = 64)
        }
        assertEquals("response_too_large", tooBig.code)

        server.enqueue(MockResponse().setBody("""{"ret":0,"pad":"${"x".repeat(200)}"}""").setHeader("Content-Length", "4096"))
        val declared = assertFailsWith<XmlyException> {
            http.postJson(base(), "/lite-mobile/like/v1/like", responseLimit = 64)
        }
        assertEquals("response_too_large", declared.code)
    }

    @Test
    fun `server errors on a write are reported as uncertain`() {
        server.enqueue(MockResponse().setResponseCode(503))
        val failure = assertFailsWith<XmlyException> {
            http.postJson(base(), "/lite-mobile/like/v1/like", readOnly = false)
        }
        assertEquals("uncertain", failure.code)
    }

    @Test
    fun `permission codes are definitive`() {
        for (code in listOf("5", "403", "726")) {
            server.enqueue(MockResponse().setBody("""{"ret":"$code"}"""))
            val failure = assertFailsWith<XmlyException> {
                http.get(base(), "/fmobile-track/fmobile/track/v2/baseInfo")
            }
            assertEquals("permission_denied", failure.code, "code $code")
        }
    }
}
