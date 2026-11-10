package teikengstudio.xmpro.model

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class CredentialsTest {
    @Test
    fun `parses the cookie shape used by the official app`() {
        val cookie = "1&_token=123456&abcdef; 1&_device=android&device-id-1&3.4.10; channel=and-f5"
        val credentials = Credentials.parse(cookie)
        assertEquals("123456&abcdef", credentials.token)
        assertEquals("device-id-1", credentials.deviceId)
        assertEquals("123456", credentials.uid)
    }

    @Test
    fun `parses json and nested wrappers`() {
        val json = """{"xmly":{"config":{"token":"123456&abcdef","device_id":"dev-1"}}}"""
        val credentials = Credentials.parse(json)
        assertEquals("123456&abcdef", credentials.token)
        assertEquals("dev-1", credentials.deviceId)
    }

    @Test
    fun `strips the login prefix and keeps identity stable`() {
        val direct = Credentials("123456&abcdef", "dev-1")
        val prefixed = Credentials("1&_token=123456&abcdef", "dev-1")
        assertEquals(direct.identity, prefixed.identity)
        assertEquals(direct.cacheIdentity, prefixed.cacheIdentity)
        assertEquals(64, direct.identity.length)
    }

    @Test
    fun `different devices get different identities`() {
        assertNotEquals(
            Credentials("123456&abcdef", "dev-1").identity,
            Credentials("123456&abcdef", "dev-2").identity,
        )
    }

    @Test
    fun `rejects ambiguous cookies and broken tokens`() {
        val ambiguous = "1&_token=123&abc; 1&_token=456&def; 1&_device=android&dev&3.4.10"
        assertEquals(
            "invalid_credentials",
            assertFailsWith<XmlyException> { Credentials.parse(ambiguous) }.code,
        )
        assertEquals(
            "invalid_credentials",
            assertFailsWith<XmlyException> { Credentials("abc", "dev") }.code,
        )
        assertEquals(
            "invalid_credentials",
            assertFailsWith<XmlyException> { Credentials("123&abc", "bad device id") }.code,
        )
    }

    @Test
    fun `never leaks the token through toString`() {
        val credentials = Credentials("123456&abcdef", "dev-1")
        assertTrue(!credentials.toString().contains("abcdef"))
    }

    @Test
    fun `reads the persisted json shape`() {
        val restored = Credentials.fromJson(mapOf("token" to "123456&abcdef", "device_id" to "dev-1"))
        assertEquals("123456&abcdef", restored.token)
        assertEquals("dev-1", restored.deviceId)
    }
}

class IdentifiersTest {
    @Test
    fun `normalizes ids and rejects anything else`() {
        assertEquals("123456", xmlyIdentifier("123456"))
        assertEquals("123456", xmlyIdentifier("xmly:123456"))
        assertFailsWith<XmlyException> { xmlyIdentifier("0") }
        assertFailsWith<XmlyException> { xmlyIdentifier("xmly:") }
        assertFailsWith<XmlyException> { xmlyIdentifier("../etc") }
    }

    @Test
    fun `positive numbers accept numeric strings only`() {
        assertEquals(7, xmlyPositive("7"))
        assertEquals(7, xmlyPositive(7))
        assertEquals(null, xmlyPositive("0"))
        assertEquals(null, xmlyPositive("-3"))
        assertEquals(null, xmlyPositive("7.5"))
        assertEquals(null, xmlyPositive(null))
    }

    @Test
    fun `covers upgrade xmcdn http links to https`() {
        assertEquals(
            "https://image.xmcdn.com/a.jpg",
            coverUrl(mapOf("coverLarge" to "http://image.xmcdn.com/a.jpg")),
        )
        assertEquals("http://example.com/a.jpg", coverUrl(mapOf("coverSmall" to "http://example.com/a.jpg")))
        assertEquals(null, coverUrl(mapOf("coverSmall" to "javascript:alert(1)")))
    }

    @Test
    fun `public metadata drops unknown and nested payloads`() {
        val raw = mapOf(
            "trackId" to 12,
            "title" to "第一集",
            "userInfo" to mapOf("nickname" to "张三", "uid" to 9),
            "tracks" to listOf(mapOf("id" to 1)),
        )
        val sanitized = publicRaw(raw)
        assertEquals(12, sanitized["trackId"])
        assertEquals("第一集", sanitized["title"])
        assertEquals(mapOf("nickname" to "张三"), sanitized["userInfo"])
        assertTrue(!sanitized.containsKey("tracks"))
    }
}
