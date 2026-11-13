package teikengstudio.xmpro.api

import java.time.Instant
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import teikengstudio.xmpro.model.Credentials

/**
 * The library tabs used to blank themselves out with "首页数据无效" whenever the
 * service reported more pages without any rows to show.
 */
class LibraryPagingTest {
    private val server = MockWebServer().apply { start() }
    private val clock: () -> Instant = { Instant.parse("2026-11-10T00:00:00Z") }

    private fun experience(): XmlyExperience {
        val base = server.url("/").toString().removeSuffix("/")
        val client = XmlyClient(
            http = XmlyHttp(clock = clock, searchOrigin = XmlyHttp.SEARCH_ORIGIN),
            credentials = Credentials("123456&abcdef", "dev-1"),
            clock = clock,
            mobile = base,
            searchOrigin = XmlyHttp.SEARCH_ORIGIN,
        )
        return XmlyExperience(client)
    }

    private fun enqueue(body: String) =
        server.enqueue(MockResponse().setBody(body).setHeader("Content-Type", "application/json"))

    @AfterTest
    fun stop() = server.shutdown()

    @Test
    fun `empty update feed ends the list instead of failing`() {
        enqueue("""{"ret":0,"data":{"trackResults":[],"hasMore":true}}""")
        val page = experience().updates()
        assertEquals(0, page.items.size)
        assertEquals(false, page.hasMore)
        assertEquals(null, page.nextTimeline)
    }

    @Test
    fun `update feed with a cursor keeps paging enabled`() {
        enqueue(
            """{"ret":0,"data":{"hasMore":true,"trackResults":[
                {"trackId":101,"title":"第一集","albumId":998,"timeline":1794575690808}
            ]}}""",
        )
        val page = experience().updates()
        assertEquals(1, page.items.size)
        assertEquals(true, page.hasMore)
        assertEquals(1794575690808L, page.nextTimeline)
    }

    @Test
    fun `empty subscription page ends the list instead of failing`() {
        enqueue("""{"ret":0,"data":{"albumResults":[],"hasMore":true}}""")
        val page = experience().subscriptions()
        assertEquals(0, page.items.size)
        assertEquals(false, page.hasMore)
    }
}
