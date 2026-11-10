package teikengstudio.xmpro.api

import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import teikengstudio.xmpro.model.Credentials
import teikengstudio.xmpro.model.XmlyException

class XmlyCryptoTest {
    @Test
    fun `play payload survives an encrypt decrypt round trip`() {
        val json = """{"trackId":123456,"bitRate":64000,"duration":301}"""
        val envelope = XmlyCrypto.encrypt(json, XmlyCrypto.playKey)
        assertTrue(envelope.endsWith("\n"), "envelope keeps the trailing newline")
        assertEquals(json, XmlyCrypto.decrypt(envelope, XmlyCrypto.playKey))
    }

    @Test
    fun `decrypt accepts unwrapped url safe base64`() {
        val json = """{"title":"第一集","ok":true}"""
        val envelope = XmlyCrypto.encrypt(json, XmlyCrypto.durationKey)
        val flattened = envelope.replace("\n", "")
        assertEquals(json, XmlyCrypto.decrypt(flattened, XmlyCrypto.durationKey))
    }

    @Test
    fun `decrypt rejects malformed input with a protocol error`() {
        for (bad in listOf("", "not base64!!", "QUJD")) {
            val failure = runCatching { XmlyCrypto.decrypt(bad, XmlyCrypto.playKey) }.exceptionOrNull()
            assertTrue(failure is XmlyException, "expected XmlyException for '$bad'")
            assertEquals("invalid_encoding", (failure as XmlyException).code)
        }
    }

    @Test
    fun `activity signature matches the reference values`() {
        assertEquals("a6fec7af506479d08f411bf040d8c762", XmlyCrypto.activitySignature(mapOf("a" to "b", "z" to "Z")))
        assertEquals("4a8f64e65478ecc6c823c708bb70c9b0", XmlyCrypto.activitySignature(mapOf("uid" to "123", "count" to "2")))
    }

    @Test
    fun `ticket encodes payload digest and suffix in order`() {
        val credentials = Credentials("123456&abcdef", "android-0123456789abcdef")
        val ticket = XmlyCrypto.freshTicket(credentials, nowSeconds = 1_700_000_000)
        assertTrue(ticket.startsWith("TAC"))
        val body = Base64.getUrlDecoder().decode(ticket.removePrefix("TAC"))
        val suffix = "com.ximalaya.ting.lite!1.3.24!3.4.10.3!b=lite&s=lite_track_play_url&&u=123456"
        assertEquals(4 + 16 + 16 + 32 + suffix.length, body.size)
        assertEquals(1_700_000_000, ((body[0].toInt() and 0xFF) shl 24) or ((body[1].toInt() and 0xFF) shl 16) or
            ((body[2].toInt() and 0xFF) shl 8) or (body[3].toInt() and 0xFF))
        assertEquals(suffix, String(body.copyOfRange(body.size - suffix.length, body.size)))
    }

    @Test
    fun `ticket honours custom attributes`() {
        val credentials = Credentials("123456&abcdef", "device")
        val ticket = XmlyCrypto.freshTicket(credentials, attributes = "custom")
        val body = Base64.getUrlDecoder().decode(ticket.removePrefix("TAC"))
        assertEquals("custom", String(body.copyOfRange(body.size - 6, body.size)))
    }
}
