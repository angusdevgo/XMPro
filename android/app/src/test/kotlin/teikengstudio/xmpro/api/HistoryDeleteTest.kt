package teikengstudio.xmpro.api

import java.time.Instant
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import teikengstudio.xmpro.model.Credentials
import teikengstudio.xmpro.model.HistoryRecord
import teikengstudio.xmpro.model.Track
import teikengstudio.xmpro.model.XmlyException

/**
 * Cloud history deletion posts the album/track pair to the collector, so the
 * form has to carry the same parameters the signature was computed over — the
 * service answers "bad parameter" otherwise.
 */
class HistoryDeleteTest {
    private val server = MockWebServer().apply { start() }
    private val now = 1_700_000_000_000L
    private val clock: () -> Instant = { Instant.ofEpochMilli(now) }

    private fun client(): XmlyClient {
        val base = server.url("/").toString().removeSuffix("/")
        return XmlyClient(
            http = XmlyHttp(clock = clock, searchOrigin = XmlyHttp.SEARCH_ORIGIN),
            credentials = Credentials("465957126&abcdef", "dev-1"),
            clock = clock,
            mobile = base,
            collector = base,
        )
    }

    private fun record() = HistoryRecord(
        track = Track(rawId = "732916741", title = "第1集", album = "专辑", albumId = "601293872"),
        positionSeconds = 61.0,
        updatedAt = now,
    )

    @AfterTest
    fun stop() = server.shutdown()

    @Test
    fun `delete posts the record and its timestamp`() {
        server.enqueue(
            MockResponse()
                .setBody("""{"ret":0,"data":{"syncPoint":1}}""")
                .setHeader("Content-Type", "application/json"),
        )

        XmlyHistory(client(), clock = { now }).delete(record())

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/nyx/v1/history/delete/android", request.path)
        val body = request.body.readUtf8()
        assertTrue(body.contains("itemId=601293872"), body)
        assertTrue(body.contains("childId=732916741"), body)
        assertTrue(body.contains("type=1"), body)
        assertTrue(body.contains("deleteTime=$now"), body)
        assertTrue(body.contains("signature="), body)
    }

    @Test
    fun `the signature covers exactly the fields that were sent`() {
        server.enqueue(
            MockResponse()
                .setBody("""{"ret":0,"data":{"syncPoint":1}}""")
                .setHeader("Content-Type", "application/json"),
        )

        XmlyHistory(client(), clock = { now }).delete(record())

        val body = server.takeRequest().body.readUtf8()
        val signature = body.substringAfter("signature=").substringBefore("&")
        val expected = XmlyHistory.signature(
            mapOf(
                "itemId" to "601293872",
                "childId" to "732916741",
                "type" to "1",
                "deleteTime" to "$now",
                "uid" to "465957126",
                "token" to "abcdef",
                "device" to "android",
                "deviceId" to "dev-1",
                "version" to "3.4.10",
                "channel" to "and-f5",
                "impl" to "com.ximalaya.ting.lite",
            ),
        )
        assertEquals(expected, signature)
    }

    @Test
    fun `a rejected delete surfaces the service reason`() {
        server.enqueue(
            MockResponse()
                .setBody("""{"ret":3,"msg":"bad parameter"}""")
                .setHeader("Content-Type", "application/json"),
        )

        try {
            XmlyHistory(client(), clock = { now }).delete(record())
            fail("expected the delete to fail")
        } catch (error: XmlyException) {
            // The transport reports the business code, which is what a user can
            // match against the official client.
            assertTrue(error.message.contains("ret=3"), error.message)
        }
    }
}
