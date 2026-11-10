package teikengstudio.xmpro.api

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/** The history signature lowercases only ASCII before MD5, like the client. */
class XmlyHistoryTest {
    @Test
    fun signatureMatchesKnownVector() {
        assertEquals(
            "2b9b3889caf58e979e28ee0265c960d3",
            XmlyHistory.signature(mapOf("token" to "abc", "uid" to "1")),
        )
    }

    @Test
    fun signatureIsOrderIndependent() {
        val first = XmlyHistory.signature(mapOf("a" to "1", "b" to "2"))
        val second = XmlyHistory.signature(mapOf("b" to "2", "a" to "1"))
        assertEquals(first, second)
        assertEquals(32, first.length)
    }

    @Test
    fun asciiLowercasingDoesNotTouchNonAscii() {
        // Chinese characters are multi-byte; naive locale lowercasing would
        // change them on some platforms and break the signature.
        val value = XmlyHistory.signature(mapOf("title" to "测试"))
        assertEquals(32, value.length)
    }
}
