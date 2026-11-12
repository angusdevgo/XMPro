package teikengstudio.xmpro.api

import java.time.Instant
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import teikengstudio.xmpro.model.XmlyException

class CommentTest {
    private val server = MockWebServer().apply { start() }
    private val clock: () -> Instant = { Instant.parse("2026-11-10T00:00:00Z") }

    private fun client() = XmlyClient(
        http = XmlyHttp(clock = clock, searchOrigin = server.url("/").toString().removeSuffix("/")),
        credentials = null,
        clock = clock,
        mobile = server.url("/").toString().removeSuffix("/"),
        searchOrigin = server.url("/").toString().removeSuffix("/"),
    )

    @AfterTest
    fun stop() {
        server.shutdown()
    }

    private fun payload(
        page: Int = 1,
        totalPages: Int = 2,
        total: Int = 12,
        comments: String = """[
            {"id":11,"uid":7,"nickname":"小王","content":"好听","createAt":"2023-09",
             "smallHeader":"//imagev2.xmcdn.com/a.jpg","likes":3,"replyCount":1,
             "replies":[{"id":12,"nickname":"主播","content":"谢谢","createAt":"2023-10"}]},
            {"id":13,"content":"   ","createAt":"2023-09"},
            {"id":14,"content":"第二行","likes":0}
        ]""",
    ): String = """
        {"ret":200,"msg":"success","data":{"currentPage":$page,"totalPage":$totalPages,
         "totalComment":$total,"pageSize":20,"comments":$comments}}
    """.trimIndent()

    @Test
    fun `comments parse, empty bodies are dropped and avatars become https`() {
        server.enqueue(MockResponse().setBody(payload()))
        val page = client().trackComments("259083794", page = 1)
        assertEquals(2, page.comments.size)
        assertEquals("11", page.comments[0].id)
        assertEquals("好听", page.comments[0].content)
        assertEquals("https://imagev2.xmcdn.com/a.jpg", page.comments[0].avatarUrl)
        assertEquals(3, page.comments[0].likes)
        assertEquals(1, page.comments[0].replies.size)
        assertEquals("谢谢", page.comments[0].replies[0].content)
        // Empty content is a deleted entry and must not reach the UI.
        assertFalse(page.comments.any { it.content.isBlank() })
        assertEquals(12, page.total)
        assertTrue(page.hasMore)
    }

    @Test
    fun `requests the search origin because the comment envelope uses ret 200`() {
        server.enqueue(MockResponse().setBody(payload(page = 2, totalPages = 2)))
        val page = client().trackComments("259083794", page = 2, pageSize = 30)
        val request = server.takeRequest()
        assertEquals("/revision/comment/queryComments?trackId=259083794&page=2&pageSize=30", request.path)
        assertEquals(2, page.page)
        assertFalse(page.hasMore)
    }

    @Test
    fun `a page size above the service limit is clamped`() {
        server.enqueue(MockResponse().setBody(payload()))
        client().trackComments("259083794", page = 1, pageSize = 500)
        assertEquals(
            "/revision/comment/queryComments?trackId=259083794&page=1&pageSize=30",
            server.takeRequest().path,
        )
    }

    @Test
    fun `an invalid track id is rejected before any request`() {
        assertTrue(runCatching { client().trackComments("") }.exceptionOrNull() is XmlyException)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `a missing data object yields an empty page`() {
        server.enqueue(MockResponse().setBody("""{"ret":200,"msg":"success"}"""))
        val page = client().trackComments("259083794")
        assertTrue(page.comments.isEmpty())
        assertEquals(0, page.total)
        assertFalse(page.hasMore)
        assertNull(page.comments.firstOrNull())
    }
}
