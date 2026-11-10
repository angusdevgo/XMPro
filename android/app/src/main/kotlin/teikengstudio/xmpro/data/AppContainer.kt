package teikengstudio.xmpro.data

import android.content.Context
import androidx.core.content.edit
import teikengstudio.xmpro.api.XmlyClient
import teikengstudio.xmpro.api.XmlyHttp
import teikengstudio.xmpro.api.XmlyRewards
import teikengstudio.xmpro.model.Credentials
import teikengstudio.xmpro.model.Quota
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

    private fun client(parsed: Credentials): XmlyClient = XmlyClient(http = http, credentials = parsed)

    val quotaSource: QuotaSource = object : QuotaSource {
        override fun quota(identity: String) = client(identity).quota(identity)
        override fun debit(identity: String, seconds: Int, operationId: String) =
            client(identity).debit(identity, seconds, operationId)
    }

    val ledger = UsageLedger(store, quotaSource)

    val downloads by lazy {
        teikengstudio.xmpro.download.DownloadManager(
            context = appContext,
            storage = store,
            directory = { downloadRoot() },
            resolve = { number ->
                val resolved = client(activeAccount).resolve(number)
                resolved.url to resolved.actualQuality
            },
        )
    }

    val playback = teikengstudio.xmpro.player.PlaybackController(appContext).apply {
        // Connecting early keeps the RPC warm; the service itself only starts
        // when a media item is set.
        runCatching { connect() }
    }

    // ------------------------------------------------------------------ session

    private val prefs = appContext.getSharedPreferences("xmpro_session", Context.MODE_PRIVATE)

    var activeAccount: String?
        get() = prefs.getString("active", null)
        private set(value) = prefs.edit { putString("active", value) }

    fun isSignedIn(identity: String?): Boolean = identity != null && credentials.load(identity) != null

    fun signIn(input: String): String = signIn(Credentials.parse(input), makePrimary = true)

    /** Validates the listening balance, then stores the account without secrets. */
    fun signIn(parsed: Credentials, makePrimary: Boolean): String {
        val quota = client(parsed).quota(parsed.identity)
        credentials.save(parsed)
        if (makePrimary || activeAccount == null) activeAccount = parsed.identity
        store.transaction { state ->
            val accounts = state.optJSONObject("accounts")
                ?: org.json.JSONObject().also { state.put("accounts", it) }
            val record = accounts.optJSONObject(parsed.identity) ?: org.json.JSONObject()
            record.put("uid", parsed.uid)
            record.put("deviceHint", hint(parsed.deviceId))
            record.put("quota", quota.toJson())
            record.put("rewardPaused", false)
            record.put("archived", false)
            if (!record.has("ledger")) record.put("ledger", org.json.JSONObject())
            accounts.put(parsed.identity, record)
            if (makePrimary || !state.has("primaryAccountId") || state.isNull("primaryAccountId")) {
                state.put("primaryAccountId", parsed.identity)
            }
            state.put("activeAccountId", activeAccount)
        }
        ledger.recover(parsed.identity)
        return parsed.identity
    }

    // ------------------------------------------------------------------ accounts

    fun accounts(): List<AccountSummary> {
        val state = store.state()
        val records = state.optJSONObject("accounts") ?: return emptyList()
        val primary = state.optString("primaryAccountId").takeIf { it.isNotEmpty() }
        val active = activeAccount
        return records.keys().asSequence()
            .filter { credentials.load(it) != null }
            .map { identity ->
                val record = records.optJSONObject(identity) ?: org.json.JSONObject()
                AccountSummary(
                    identity = identity,
                    uid = record.optString("uid"),
                    deviceHint = record.optString("deviceHint", "****"),
                    quota = record.optJSONObject("quota"),
                    paused = record.optBoolean("rewardPaused", false),
                    primary = identity == primary,
                    active = identity == active,
                )
            }
            .sortedWith(compareByDescending<AccountSummary> { it.primary }.thenByDescending { it.active })
            .toList()
    }

    fun setPrimaryAccount(identity: String) {
        if (credentials.load(identity) == null) throw XmlyException("missing_account", "请先登录该账号")
        activeAccount = identity
        store.transaction { state ->
            state.put("primaryAccountId", identity)
            state.put("activeAccountId", identity)
        }
    }

    fun removeAccount(identity: String) {
        signOut(identity)
        store.transaction { state ->
            val accounts = state.optJSONObject("accounts") ?: return@transaction
            accounts.remove(identity)
            if (accounts.length() == 0) {
                state.put("primaryAccountId", org.json.JSONObject.NULL)
                state.put("activeAccountId", org.json.JSONObject.NULL)
                return@transaction
            }
            if (state.optString("primaryAccountId") == identity) {
                val next = accounts.keys().asSequence().firstOrNull()
                state.put("primaryAccountId", next ?: org.json.JSONObject.NULL)
                if (activeAccount == null) activeAccount = next
                state.put("activeAccountId", activeAccount ?: org.json.JSONObject.NULL)
            }
        }
    }

    /** Re-reads the official balance and stores it for the account card. */
    fun refreshQuota(identity: String): Quota {
        val quota = client(identity).quota(identity)
        store.transaction { state ->
            state.optJSONObject("accounts")?.optJSONObject(identity)?.put("quota", quota.toJson())
        }
        return quota
    }

    /** One manual claim; a failure pauses automatic claims for that account. */
    fun claimOnce(identity: String): Quota {
        return try {
            val client = client(identity)
            XmlyRewards(client).claim()
            val quota = client.quota(identity)
            store.transaction { state ->
                state.optJSONObject("accounts")?.optJSONObject(identity)?.put("quota", quota.toJson())
            }
            quota
        } catch (error: Exception) {
            store.transaction { state ->
                state.optJSONObject("accounts")?.optJSONObject(identity)?.put("rewardPaused", true)
            }
            throw error
        }
    }

    fun resumeRewards(identity: String) {
        store.transaction { state ->
            state.optJSONObject("accounts")?.optJSONObject(identity)?.put("rewardPaused", false)
        }
    }

    fun autoClaim(): Boolean = store.state().optJSONObject("settings")?.optBoolean("autoClaim", false) ?: false

    fun setAutoClaim(enabled: Boolean) {
        store.transaction { state ->
            val settings = state.optJSONObject("settings")
                ?: org.json.JSONObject().also { state.put("settings", it) }
            settings.put("autoClaim", enabled)
        }
    }

    /**
     * Top-up pass for the account list: refreshes the balance and, when
     * automatic claiming is on, refills from below 2 h up to 4 h.
     */
    fun refreshRewards(identity: String): Quota {
        val client = client(identity)
        val rewards = XmlyRewards(client)
        val quota = rewards.refresh(autoClaim()) { client.quota(identity) }
        store.transaction { state ->
            val record = state.optJSONObject("accounts")?.optJSONObject(identity) ?: return@transaction
            record.put("quota", quota.toJson())
            if (rewards.lastError.isNotEmpty()) record.put("rewardPaused", true)
        }
        return quota
    }

    private fun hint(deviceId: String): String =
        if (deviceId.length <= 4) "****" else "…" + deviceId.takeLast(4)

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
