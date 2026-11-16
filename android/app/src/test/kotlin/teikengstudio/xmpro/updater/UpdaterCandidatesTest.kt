package teikengstudio.xmpro.updater

import kotlin.test.Test
import kotlin.test.assertEquals

class UpdaterCandidatesTest {
    private val raw = "https://github.com/a/b/releases/download/v1/x.apk"
    private val mirrors = listOf("", "https://m1/", "https://m2/")

    @Test
    fun `the user's mirror is tried first, then direct, then the rest`() {
        assertEquals(
            listOf("https://m2/$raw", raw, "https://m1/$raw"),
            Updater.candidates("https://m2/$raw", raw, mirrors),
        )
    }

    @Test
    fun `without a mirror the plain link leads`() {
        assertEquals(
            listOf(raw, "https://m1/$raw", "https://m2/$raw"),
            Updater.candidates("", raw, mirrors),
        )
    }

    @Test
    fun `a mirror that matches the preference is not repeated`() {
        val list = Updater.candidates("https://m1/$raw", raw, mirrors)
        assertEquals(1, list.count { it == "https://m1/$raw" })
    }

    @Test
    fun `an empty preference is the same as none`() {
        assertEquals(
            Updater.candidates("", raw, mirrors),
            Updater.candidates(raw, raw, mirrors),
        )
    }
}
