package teikengstudio.xmpro.api

import java.util.Base64
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The RSA block and the login signature must stay byte-identical to the
 * official client, otherwise the passport service rejects every SMS request.
 */
class XmlyLoginTest {
    @Test
    fun encryptedMobileIsOneHundredAndTwentyEightBytes() {
        val bytes = Base64.getDecoder().decode(XmlyLogin.encryptMobile("13800000000"))
        // 1024-bit RSA output: always a full 128-byte block, never truncated.
        assertEquals(128, bytes.size)
        assertTrue(bytes.any { it.toInt() != 0 })
    }

    @Test
    fun encryptionUsesFreshPadding() {
        val first = XmlyLogin.encryptMobile("13800000000")
        val second = XmlyLogin.encryptMobile("13800000000")
        // PKCS#1 v1.5 padding is random per call; identical output would let a
        // passive observer correlate sign-in attempts.
        assertNotEquals(first, second)
    }

    @Test
    fun signatureIsOrderIndependentAndHex() {
        val first = XmlyLogin.signature(mapOf("b" to "2", "a" to "1"))
        val second = XmlyLogin.signature(mapOf("a" to "1", "b" to "2"))
        assertEquals(first, second)
        assertEquals(40, first.length)
        assertTrue(first.all { it in "0123456789abcdef" })
    }

    @Test
    fun captchaFieldsAreTheFiveOfficialOnes() {
        assertEquals(
            listOf("captcha_id", "captcha_output", "gen_time", "lot_number", "pass_token"),
            XmlyLogin.FIELDS.sorted(),
        )
        assertEquals(32, XmlyLogin.CAPTCHA_ID.length)
    }
}
