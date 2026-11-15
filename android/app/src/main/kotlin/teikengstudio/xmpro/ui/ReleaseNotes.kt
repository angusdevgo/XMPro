package teikengstudio.xmpro.ui

/**
 * Parsed release body, ready to draw.
 *
 * A GitHub release for this project is written like the CHANGELOG: `### 新增`,
 * `### 优化`, `### 修复` headings with `- ` bullets underneath. Dumping that as
 * one flat block loses the grouping the author put there, so the dialog renders
 * headings and bullets as separate rows instead.
 */
class ReleaseNotes(val sections: List<Section>) {

    class Section(val title: String, val items: List<String>)

    val isEmpty: Boolean get() = sections.all { it.items.isEmpty() }

    companion object {
        /** Lines that look like a group heading, once markers are stripped. */
        private val HEADING = Regex("^#{2,4}\\s*(.+)$")
        private val BOLD = Regex("^\\*\\*(.+)\\*\\*$")
        private val BULLET = Regex("^[-*·]\\s+(.*)$")

        fun parse(raw: String?): ReleaseNotes {
            if (raw.isNullOrBlank()) return ReleaseNotes(emptyList())
            val sections = mutableListOf<Section>()
            var title = ""
            val items = mutableListOf<String>()

            fun flush() {
                if (title.isNotEmpty() || items.isNotEmpty()) {
                    sections += Section(title, items.toList())
                }
                title = ""
                items.clear()
            }

            raw.lineSequence().forEach { line ->
                val text = line.trim()
                when {
                    text.isEmpty() -> Unit
                    // The version heading repeats the dialog title, drop it.
                    text.startsWith("## ") || text == "##" -> flush()
                    HEADING.matches(text) || BOLD.matches(text) -> {
                        val heading = HEADING.find(text)?.groupValues?.get(1)
                            ?: BOLD.find(text)?.groupValues?.get(1)
                            ?: text
                        flush()
                        title = heading.trim()
                    }
                    BULLET.matches(text) -> items += BULLET.find(text)?.groupValues?.get(1)?.trim().orEmpty()
                    else -> items += text
                }
            }
            flush()
            return ReleaseNotes(sections.filter { it.title.isNotEmpty() || it.items.isNotEmpty() })
        }
    }
}
