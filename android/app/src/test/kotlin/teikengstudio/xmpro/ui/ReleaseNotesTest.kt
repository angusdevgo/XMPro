package teikengstudio.xmpro.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class ReleaseNotesTest {

    @Test
    fun `markdown headings and dashes are dropped`() {
        val body = """
            ## 0.1.5

            ### 修复

            - 修复甲
            - 修复乙
        """.trimIndent()
        assertEquals("· 修复甲\n· 修复乙", formatReleaseNotes(body))
    }

    @Test
    fun `plain text is kept as is`() {
        assertEquals("普通说明\n第二行", formatReleaseNotes("普通说明\n第二行"))
    }

    @Test
    fun `empty notes stay empty`() {
        assertEquals("", formatReleaseNotes(""))
    }
}
