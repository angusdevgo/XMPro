package teikengstudio.xmpro.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReleaseNotesTest {

    private val body = """
        ## 0.1.13

        ### 优化

        - 回到前台也会检查更新
        - 更新日志分组显示

        ### 修复

        - 修复常驻后台不再检查更新
    """.trimIndent()

    @Test
    fun `groups survive parsing`() {
        val notes = ReleaseNotes.parse(body)
        assertEquals(listOf("优化", "修复"), notes.sections.map { it.title })
        assertEquals(
            listOf("回到前台也会检查更新", "更新日志分组显示"),
            notes.sections.first().items,
        )
        assertEquals(listOf("修复常驻后台不再检查更新"), notes.sections.last().items)
    }

    @Test
    fun `the version heading the dialog already shows is dropped`() {
        val notes = ReleaseNotes.parse(body)
        assertTrue(notes.sections.none { it.title.startsWith("0.1.13") })
    }

    @Test
    fun `bold headings and star bullets are understood too`() {
        val notes = ReleaseNotes.parse("**新增**\n\n* 离线下载\n* 下载页")
        assertEquals("新增", notes.sections.single().title)
        assertEquals(listOf("离线下载", "下载页"), notes.sections.single().items)
    }

    @Test
    fun `notes without headings still render as bullets`() {
        val notes = ReleaseNotes.parse("普通说明\n第二行")
        assertEquals("", notes.sections.single().title)
        assertEquals(listOf("普通说明", "第二行"), notes.sections.single().items)
    }

    @Test
    fun `blank notes are reported as empty`() {
        assertTrue(ReleaseNotes.parse("").isEmpty)
        assertTrue(ReleaseNotes.parse(null).isEmpty)
        assertTrue(ReleaseNotes.parse("   \n\n  ").isEmpty)
    }
}
