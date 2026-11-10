package teikengstudio.xmpro.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import teikengstudio.xmpro.model.Credentials

/**
 * Account tokens live only in an encrypted preference file; `state.json` never
 * contains them. Keys are the stable account identity hash.
 */
class SecureCredentials(context: Context) {
    private val prefs = EncryptedSharedPreferences.create(
        context,
        "xmpro_credentials",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun save(credentials: Credentials) {
        prefs.edit()
            .putString("${credentials.identity}.token", credentials.token)
            .putString("${credentials.identity}.device", credentials.deviceId)
            .apply()
    }

    fun load(identity: String): Credentials? {
        val token = prefs.getString("$identity.token", null) ?: return null
        val device = prefs.getString("$identity.device", null) ?: return null
        return runCatching { Credentials(token, device) }.getOrNull()
    }

    fun remove(identity: String) {
        prefs.edit()
            .remove("$identity.token")
            .remove("$identity.device")
            .apply()
    }

    fun identities(): List<String> =
        prefs.all.keys.mapNotNull { key -> key.substringBefore('.').takeIf { key.endsWith(".token") } }
}
