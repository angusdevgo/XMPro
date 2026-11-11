package teikengstudio.xmpro.data

import android.content.ContentResolver
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import teikengstudio.xmpro.model.Credentials

/**
 * Whole-app backup: `state.json` plus (optionally) the account tokens, so a
 * restore on a new phone keeps the user signed in.
 *
 * The file is plain JSON on purpose — it leaves the device only through a
 * document picker the user drives, and tokens are useless without the
 * Ximalaya account they belong to.
 */
class BackupManager(private val container: AppContainer) {

    private class Entry(val identity: String, val credentials: Credentials)

    fun export(resolver: ContentResolver, target: Uri, includeCredentials: Boolean): Int {
        val state = container.storageState()
        val payload = JSONObject().apply {
            put("app", "XMPro")
            put("version", 1)
            put("exportedAt", System.currentTimeMillis())
            put("state", state)
            if (includeCredentials) {
                val accounts = JSONArray()
                for (identity in container.credentialIdentities()) {
                    val credentials = container.credentialsFor(identity) ?: continue
                    accounts.put(
                        JSONObject().apply {
                            put("identity", identity)
                            put("token", credentials.token)
                            put("deviceId", credentials.deviceId)
                        },
                    )
                }
                put("accounts", accounts)
            }
        }
        val bytes = payload.toString().toByteArray()
        resolver.openOutputStream(target)?.use { stream ->
            stream.write(bytes)
            stream.flush()
        } ?: throw IllegalStateException("无法写入备份文件")
        return bytes.size
    }

    fun import(resolver: ContentResolver, source: Uri): ImportResult {
        val text = resolver.openInputStream(source)?.use { it.readBytes().decodeToString() }
            ?: throw IllegalArgumentException("无法读取备份文件")
        val payload = runCatching { JSONObject(text) }
            .getOrElse { throw IllegalArgumentException("备份文件不是有效的 JSON") }
        if (payload.optString("app") != "XMPro") throw IllegalArgumentException("不是 XMPro 的备份文件")
        val state = payload.optJSONObject("state") ?: throw IllegalArgumentException("备份缺少资料数据")
        if (state.optInt("schemaVersion", -1) != 1) throw IllegalArgumentException("备份版本不受支持")

        val accounts = payload.optJSONArray("accounts")
        val restored = mutableListOf<Entry>()
        if (accounts != null) {
            for (index in 0 until accounts.length()) {
                val item = accounts.optJSONObject(index) ?: continue
                val token = item.optString("token")
                val device = item.optString("deviceId")
                if (token.isEmpty() || device.isEmpty()) continue
                val credentials = runCatching { Credentials(token, device) }.getOrNull() ?: continue
                restored.add(Entry(item.optString("identity").ifEmpty { credentials.identity }, credentials))
            }
        }

        container.replaceState(state)
        for (entry in restored) container.saveCredentials(entry.credentials)
        return ImportResult(
            accounts = restored.size,
            subscriptions = state.optJSONObject("library")?.length() ?: 0,
        )
    }

    data class ImportResult(val accounts: Int, val subscriptions: Int)
}
