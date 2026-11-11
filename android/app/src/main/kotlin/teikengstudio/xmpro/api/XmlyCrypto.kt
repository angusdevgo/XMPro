package teikengstudio.xmpro.api

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import teikengstudio.xmpro.model.Credentials
import teikengstudio.xmpro.model.XmlyException

/**
 * Protocol cryptography ported from xmly/client.py (AGPL-3.0-or-later).
 *
 * Every constant and byte layout here must stay identical to the Dart
 * implementation; a mismatch silently produces unplayable URLs.
 */
object XmlyCrypto {
    private val playKeyBytes = hex("bf56792c145e706d1dd26c04feba6a8c")
    private val durationKeyBytes = "jkq1hsksjxg4mf2y".toByteArray(Charsets.US_ASCII)
    private val ticketMaterial = hex("b0b1a8ac34e66efa95c7f4157cf8b6ba33dfc2075e41fb964f2e12dcaa")
    private const val ACTIVITY_MATERIAL = "q35435432sadks2i3546p2ndkcaqiwurhqfebt4kn"

    val playKey: ByteArray get() = playKeyBytes.copyOf()
    val durationKey: ByteArray get() = durationKeyBytes.copyOf()

    private fun hex(value: String): ByteArray =
        ByteArray(value.length / 2) { index ->
            value.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }

    private fun cipher(mode: Int, key: ByteArray): Cipher =
        Cipher.getInstance("AES/ECB/PKCS5Padding").apply {
            init(mode, SecretKeySpec(key, "AES"))
        }

    /** Serializes [value] (already JSON encoded) into the line-wrapped envelope. */
    fun encrypt(json: String, key: ByteArray): String {
        val raw = json.toByteArray()
        if (raw.size > 2 * 1024 * 1024) throw XmlyException("invalid_encoding", "协议数据过大")
        val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(cipher(Cipher.ENCRYPT_MODE, key).doFinal(raw))
        return encoded.chunked(76).joinToString("\n") + "\n"
    }

    /** Accepts the newline wrapped, url-safe base64 envelope used by the API. */
    fun decrypt(value: String, key: ByteArray): String {
        try {
            if (value.length > 4 * 1024 * 1024) throw IllegalArgumentException("too long")
            val text = value.replace("\r", "").replace("\n", "")
            if (!Regex("^[A-Za-z0-9+/_\\-]*={0,2}$").matches(text)) {
                throw IllegalArgumentException("invalid alphabet")
            }
            val normalized = text.replace('-', '+').replace('_', '/')
            val padded = normalized + "=".repeat((4 - normalized.length % 4) % 4)
            val bytes = Base64.getDecoder().decode(padded)
            if (bytes.isEmpty() || bytes.size % 16 != 0) throw IllegalArgumentException("invalid length")
            return cipher(Cipher.DECRYPT_MODE, key).doFinal(bytes).toString(Charsets.UTF_8)
        } catch (_: Exception) {
            throw XmlyException("invalid_encoding", "喜马拉雅播放协议解码失败，可能需要更新客户端协议")
        }
    }

    /**
     * Activity API signature: keys sorted, `k=v&` concatenated, lower-cased
     * ASCII, then MD5 over value plus the shared material.
     */
    fun activitySignature(params: Map<String, String>): String {
        val canonical = buildString {
            for (key in params.keys.sorted()) {
                append(key).append('=').append(params[key]).append('&')
            }
            append(ACTIVITY_MATERIAL)
        }
        val lowered = canonical.toByteArray(Charsets.UTF_8).map { byte ->
            if (byte in 65..90) (byte + 32).toByte() else byte
        }.toByteArray()
        return MessageDigest.getInstance("MD5").digest(lowered).joinToString("") { "%02x".format(it) }
    }

    /** Builds the TAC ticket used by authenticated track endpoints. */
    fun freshTicket(credentials: Credentials, attributes: String? = null, nowSeconds: Long? = null): String {
        val identity = MessageDigest.getInstance("MD5")
            .digest("${credentials.uid}|${credentials.deviceId}".toByteArray())
        val random = SecureRandom()
        val uuid = ByteArray(16).also(random::nextBytes)
        uuid[6] = ((uuid[6].toInt() and 0x0F) or 0x40).toByte()
        uuid[8] = ((uuid[8].toInt() and 0x3F) or 0x80).toByte()
        val seconds = nowSeconds ?: (ServerClock.now() / 1000)
        val payload = ByteArray(4 + 16 + 16)
        payload[0] = ((seconds shr 24) and 0xFF).toByte()
        payload[1] = ((seconds shr 16) and 0xFF).toByte()
        payload[2] = ((seconds shr 8) and 0xFF).toByte()
        payload[3] = (seconds and 0xFF).toByte()
        identity.copyInto(payload, 4, 0, 16)
        for (index in 0 until 16) {
            payload[20 + index] = (uuid[index].toInt() xor identity[index].toInt()).toByte()
        }
        val suffix = ("com.ximalaya.ting.lite!1.3.24!3.4.10.3!" +
            (attributes ?: "b=lite&s=lite_track_play_url&&u=${credentials.uid}")).toByteArray()
        val digest = MessageDigest.getInstance("SHA-256").run {
            update(payload)
            update("TAC".toByteArray(Charsets.US_ASCII))
            update(ticketMaterial)
            update(suffix)
            digest()
        }
        val body = payload + digest + suffix
        return "TAC" + Base64.getUrlEncoder().withoutPadding().encodeToString(body)
    }
}
