package teikengstudio.xmpro.data

import android.content.Context
import androidx.core.content.edit
import teikengstudio.xmpro.api.XmlyClient
import teikengstudio.xmpro.api.XmlyHttp
import teikengstudio.xmpro.model.Credentials
import teikengstudio.xmpro.model.XmlyException
import java.io.File

/**
 * Process-wide service graph. Everything is created lazily so an unused service
 * never touches disk, network or the keystore.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val store: AppStorage = AppStorage.open(
        File(appContext.getExternalFilesDir(null) ?: appContext.filesDir, "xmpro").absolutePath,
    )

    val credentials = SecureCredentials(appContext)

    private val http = XmlyHttp()

    /** Account-bound client, or the anonymous client when [identity] is null. */
    fun client(identity: String?): XmlyClient =
        if (identity == null) {
            XmlyClient(http = http)
        } else {
            val credential = credentials.load(identity)
                ?: throw XmlyException("missing_account", "请先登录原账号")
            XmlyClient(http = http, credentials = credential)
        }

    val quotaSource: QuotaSource = object : QuotaSource {
        override fun quota(identity: String) = client(identity).quota(identity)
        override fun debit(identity: String, seconds: Int, operationId: String) =
            client(identity).debit(identity, seconds, operationId)
    }

    val ledger = UsageLedger(store, quotaSource)

    // ------------------------------------------------------------------ session

    private val prefs = appContext.getSharedPreferences("xmpro_session", Context.MODE_PRIVATE)

    var activeAccount: String?
        get() = prefs.getString("active", null)
        private set(value) = prefs.edit { putString("active", value) }

    fun isSignedIn(identity: String?): Boolean = identity != null && credentials.load(identity) != null

    fun signIn(input: String): String {
        val parsed = Credentials.parse(input)
        credentials.save(parsed)
        activeAccount = parsed.identity
        store.transaction { state ->
            val accounts = state.getJSONObject("accounts")
            if (!accounts.has(parsed.identity)) accounts.put(parsed.identity, org.json.JSONObject())
            state.put("activeAccountId", parsed.identity)
        }
        ledger.recover(parsed.identity)
        return parsed.identity
    }

    fun signOut(identity: String?) {
        if (identity == null) return
        credentials.remove(identity)
        if (activeAccount == identity) activeAccount = null
        store.transaction { state ->
            if (state.optString("activeAccountId") == identity) {
                state.put("activeAccountId", org.json.JSONObject.NULL)
            }
        }
    }

    // ---------------------------------------------------------------- settings

    fun themeMode(): String = store.state().optJSONObject("settings")
        ?.optJSONObject("appearance")?.optString("themeMode")?.takeIf { it.isNotEmpty() } ?: "system"

    fun oledBlack(): Boolean =
        store.state().optJSONObject("settings")?.optJSONObject("appearance")?.optBoolean("oledBlack") ?: false

    fun setThemeMode(mode: String) {
        store.transaction { state ->
            val settings = state.optJSONObject("settings") ?: org.json.JSONObject().also { state.put("settings", it) }
            val appearance = settings.optJSONObject("appearance") ?: org.json.JSONObject().also { settings.put("appearance", it) }
            appearance.put("themeMode", mode)
        }
    }

    fun setOledBlack(enabled: Boolean) {
        store.transaction { state ->
            val settings = state.optJSONObject("settings") ?: org.json.JSONObject().also { state.put("settings", it) }
            val appearance = settings.optJSONObject("appearance") ?: org.json.JSONObject().also { settings.put("appearance", it) }
            appearance.put("oledBlack", enabled)
        }
    }

    /** Completed audio files, newest first. */
    fun downloads(): List<File> {
        val root = downloadRoot()
        return root.listFiles { file -> file.isFile && file.length() > 0 }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }

    fun downloadRoot(): File =
        File(appContext.getExternalFilesDir(null) ?: appContext.filesDir, "downloads").apply { mkdirs() }

    fun close() {
        store.close()
    }
}
