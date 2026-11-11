package teikengstudio.xmpro.data

import android.content.Context
import androidx.core.content.edit
import teikengstudio.xmpro.api.XmlyClient
import teikengstudio.xmpro.api.XmlyHttp
import teikengstudio.xmpro.api.XmlyExperience
import teikengstudio.xmpro.api.XmlyRewards
import teikengstudio.xmpro.model.Credentials
import teikengstudio.xmpro.model.Quota
import teikengstudio.xmpro.model.Track
import teikengstudio.xmpro.model.XmlyException
import java.io.File

/**
 * Process-wide service graph. Everything is created lazily so an unused service
 * never touches disk, network or the keystore.
 */
/** Trim points are stored per-thousand of the track; this caps each side. */
const val MAX_SKIP_PERMILLE = 400

/** Default trim per side when the feature is switched on: 3% of the track. */
const val DEFAULT_SKIP_PERMILLE = 30

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

    val playback = teikengstudio.xmpro.player.PlaybackController(appContext).apply {
        // Connecting early keeps the RPC warm; the service itself only starts
        // when a media item is set.
        runCatching { connect() }
    }

    // ------------------------------------------------------------------ session

    private val prefs = appContext.getSharedPreferences("xmpro_session", Context.MODE_PRIVATE)

    /** One device id per install, reused by every login and cookie. */
    val deviceId: String = prefs.getString("deviceId", null) ?: uuid().also { id ->
        prefs.edit { putString("deviceId", id) }
    }

    private fun uuid(): String {
        val bytes = ByteArray(16).also { java.security.SecureRandom().nextBytes(it) }
        bytes[6] = ((bytes[6].toInt() and 0x0F) or 0x40).toByte()
        bytes[8] = ((bytes[8].toInt() and 0x3F) or 0x80).toByte()
        val hex = bytes.joinToString("") { "%02x".format(it) }
        return "${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-" +
            "${hex.substring(16, 20)}-${hex.substring(20)}"
    }

    var activeAccount: String?
        get() = prefs.getString("active", null)
        private set(value) = prefs.edit { putString("active", value) }

    fun isSignedIn(identity: String?): Boolean = identity != null && credentials.load(identity) != null

    fun signIn(input: String): String = signIn(Credentials.parse(input), makePrimary = true)

    /** Stores an account created by SMS login together with its profile. */
    fun signIn(result: teikengstudio.xmpro.model.LoginResult, makePrimary: Boolean): String {
        val identity = signIn(result.credentials, makePrimary)
        if (result.nickname.isNotEmpty() || result.avatarUrl != null || result.mobile.isNotEmpty()) {
            store.transaction { state ->
                val record = state.optJSONObject("accounts")?.optJSONObject(identity) ?: return@transaction
                if (result.nickname.isNotEmpty()) record.put("nickname", result.nickname)
                result.avatarUrl?.let { record.put("avatar", it) }
                if (result.mobile.isNotEmpty()) record.put("mobile", maskMobile(result.mobile))
            }
        }
        return identity
    }

    private fun maskMobile(mobile: String): String =
        if (mobile.length >= 7) mobile.take(3) + "****" + mobile.takeLast(4) else mobile

    /**
     * Stores the account without secrets.
     *
     * The balance read is advisory: a rejected or unreachable quota call must
     * not lock a valid login out, so it is recorded as unverified instead.
     */
    fun signIn(parsed: Credentials, makePrimary: Boolean): String {
        val quota = runCatching { client(parsed).quota(parsed.identity) }.getOrNull()
        credentials.save(parsed)
        // Always activate something: an imported account that is not promoted to
        // primary must still be usable, otherwise the app looks logged out.
        activeAccount = when {
            makePrimary -> parsed.identity
            activeAccount != null -> activeAccount
            else -> parsed.identity
        }
        store.transaction { state ->
            val accounts = state.optJSONObject("accounts")
                ?: org.json.JSONObject().also { state.put("accounts", it) }
            val record = accounts.optJSONObject(parsed.identity) ?: org.json.JSONObject()
            record.put("uid", parsed.uid)
            record.put("deviceHint", hint(parsed.deviceId))
            if (quota != null) {
                record.put("quota", quota.toJson())
                record.remove("quotaError")
            } else {
                record.remove("quota")
                record.put("quotaError", true)
            }
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
        runCatching { refreshProfile(parsed.identity) }
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
                    quotaRejected = record.optBoolean("quotaError", false),
                    nickname = record.optString("localName").ifEmpty { record.optString("nickname") },
                    avatarUrl = record.optString("avatar").takeIf { it.isNotEmpty() },
                    mobile = record.optString("mobile"),
                    followers = record.optInt("followers", -1).takeIf { it >= 0 },
                    following = record.optInt("following", -1).takeIf { it >= 0 },
                    subscriptionCount = record.optInt("subscriptionCount", -1).takeIf { it >= 0 },
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

    /** Fetches the account nickname/avatar and caches it in state.json. */
    fun refreshProfile(identity: String): teikengstudio.xmpro.model.UserProfile {
        val client = client(identity)
        val profile = XmlyExperience(client).profile()
        store.transaction { state ->
            val record = state.optJSONObject("accounts")?.optJSONObject(identity) ?: return@transaction
            if (profile.nickname.isNotEmpty()) record.put("nickname", profile.nickname)
            profile.avatarUrl?.let { record.put("avatar", it) }
            if (profile.intro.isNotEmpty()) record.put("intro", profile.intro)
            profile.followers?.let { record.put("followers", it) }
            profile.following?.let { record.put("following", it) }
            profile.subscriptions?.let { record.put("subscriptionCount", it) }
        }
        return profile
    }

    /** Local alias used when the official profile cannot be read. */
    fun renameAccount(identity: String, nickname: String) {
        store.transaction { state ->
            state.optJSONObject("accounts")?.optJSONObject(identity)?.put("localName", nickname.trim())
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
            // A definitive refusal (daily cap, balance cap) says nothing about the
            // account being broken, so it must not disable claiming permanently.
            val definitive = error is XmlyException && error.definitive
            if (!definitive) {
                store.transaction { state ->
                    state.optJSONObject("accounts")?.optJSONObject(identity)?.put("rewardPaused", true)
                }
            }
            throw error
        }
    }

    /** Welfare task list as plain JSON, for the diagnostics dialog. */
    fun rewardTasks(identity: String): String = XmlyRewards(client(identity)).taskDiagnostics()

    fun rewardTiers(identity: String): List<teikengstudio.xmpro.api.RewardTier> =
        XmlyRewards(client(identity)).tiers()

    /** Raw listening-time payload, for the diagnostics dialog. */
    fun quotaDiagnostics(identity: String): String = client(identity).quotaDiagnostics()

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

    // ---------------------------------------------------------------- bookmarks

    fun bookmarks(): List<Bookmark> {
        val identity = activeAccount ?: return emptyList()
        val library = store.state().optJSONObject("library")?.optJSONObject(identity)
        val items = library?.optJSONArray("bookmarks") ?: return emptyList()
        return (0 until items.length()).mapNotNull { index ->
            items.optJSONObject(index)?.let { Bookmark.fromJson(it) }
        }.sortedByDescending { it.createdAt }
    }

    /** Adds or refreshes the bookmark for one track. */
    fun addBookmark(track: Track, positionMs: Long): List<Bookmark> {
        val identity = activeAccount ?: return emptyList()
        val position = positionMs.coerceAtLeast(0)
        store.transaction { state ->
            val library = state.optJSONObject("library")
                ?: org.json.JSONObject().also { state.put("library", it) }
            val account = library.optJSONObject(identity)
                ?: org.json.JSONObject().also { library.put(identity, it) }
            val existing = account.optJSONArray("bookmarks") ?: org.json.JSONArray()
            val kept = org.json.JSONArray()
            for (index in 0 until existing.length()) {
                val item = existing.optJSONObject(index) ?: continue
                if (Bookmark.fromJson(item)?.track?.id == track.id) continue
                kept.put(item)
            }
            kept.put(Bookmark(track, position, System.currentTimeMillis()).toJson())
            account.put("bookmarks", kept)
        }
        return bookmarks()
    }

    fun removeBookmark(trackId: String): List<Bookmark> {
        val identity = activeAccount ?: return emptyList()
        store.transaction { state ->
            val account = state.optJSONObject("library")?.optJSONObject(identity) ?: return@transaction
            val existing = account.optJSONArray("bookmarks") ?: return@transaction
            val kept = org.json.JSONArray()
            for (index in 0 until existing.length()) {
                val item = existing.optJSONObject(index) ?: continue
                if (Bookmark.fromJson(item)?.track?.id == trackId) continue
                kept.put(item)
            }
            account.put("bookmarks", kept)
        }
        return bookmarks()
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

    /** Android 12+ wallpaper palette switch. */
    fun dynamicColor(): Boolean =
        store.state().optJSONObject("settings")?.optJSONObject("appearance")?.optBoolean("dynamicColor")
            ?: false

    fun setDynamicColor(enabled: Boolean) {
        store.transaction { state ->
            val settings = state.optJSONObject("settings") ?: org.json.JSONObject().also { state.put("settings", it) }
            val appearance = settings.optJSONObject("appearance") ?: org.json.JSONObject().also { settings.put("appearance", it) }
            appearance.put("dynamicColor", enabled)
        }
    }

    /** Seed colour packed as ARGB; the coral default matches the old client. */
    fun seedColor(): Int = store.state().optJSONObject("settings")
        ?.optJSONObject("appearance")?.optInt("seedColor", 0)?.takeIf { it != 0 } ?: 0xFFE57373.toInt()

    fun setSeedColor(argb: Int) {
        store.transaction { state ->
            val settings = state.optJSONObject("settings") ?: org.json.JSONObject().also { state.put("settings", it) }
            val appearance = settings.optJSONObject("appearance") ?: org.json.JSONObject().also { settings.put("appearance", it) }
            appearance.put("seedColor", argb)
        }
    }

    // ---------------------------------------------------------------- playback

    /** Requested audio quality: auto, m4a_128, m4a_64 or mp3_32. */
    fun playbackQuality(): String = store.state().optJSONObject("settings")
        ?.optJSONObject("playback")?.optString("quality")?.takeIf { it.isNotEmpty() } ?: "auto"

    fun setPlaybackQuality(quality: String) {
        store.transaction { state ->
            val settings = state.optJSONObject("settings") ?: org.json.JSONObject().also { state.put("settings", it) }
            val playback = settings.optJSONObject("playback") ?: org.json.JSONObject().also { settings.put("playback", it) }
            playback.put("quality", quality)
        }
    }

    /**
     * Skip points as a share of the track, in per-thousand.
     *
     * A fixed second count cannot serve both a 3-minute clip and a 40-minute
     * episode, so the trim points follow the track length instead.
     */
    /** App playback volume, 0..1; kept across albums and sessions. */
    fun playVolume(): Float = store.state().optJSONObject("settings")
        ?.optJSONObject("playback")?.optDouble("volume", 1.0)?.toFloat()?.coerceIn(0f, 1f) ?: 1f

    fun setPlayVolume(volume: Float) {
        store.transaction { state ->
            val settings = state.optJSONObject("settings") ?: org.json.JSONObject().also { state.put("settings", it) }
            val playback = settings.optJSONObject("playback") ?: org.json.JSONObject().also { settings.put("playback", it) }
            playback.put("volume", volume.coerceIn(0f, 1f).toDouble())
        }
    }

    /** GitHub mirror prefix used by the update check; empty means direct. */
    fun updateMirror(): String = store.state().optJSONObject("settings")
        ?.optString("updateMirror") ?: ""

    fun setUpdateMirror(mirror: String) {
        store.transaction { state ->
            val settings = state.optJSONObject("settings") ?: org.json.JSONObject().also { state.put("settings", it) }
            settings.put("updateMirror", mirror)
        }
    }

    /** `smart` skips silence automatically, `manual` uses the trim points. */
    fun skipTrimMode(): String = store.state().optJSONObject("settings")
        ?.optJSONObject("playback")?.optString("skipMode")?.takeIf { it.isNotEmpty() }
        ?: teikengstudio.xmpro.player.SmartSkip.MODE_SMART

    /** Whether the trim points are applied at all. */
    fun skipTrimEnabled(): Boolean = store.state().optJSONObject("settings")
        ?.optJSONObject("playback")?.optBoolean("skipTrim", false) ?: false

    fun introSkipPermille(): Int = store.state().optJSONObject("settings")
        ?.optJSONObject("playback")?.optInt("introPermille", DEFAULT_SKIP_PERMILLE) ?: DEFAULT_SKIP_PERMILLE

    fun outroSkipPermille(): Int = store.state().optJSONObject("settings")
        ?.optJSONObject("playback")?.optInt("outroPermille", DEFAULT_SKIP_PERMILLE) ?: DEFAULT_SKIP_PERMILLE

    fun setSkipTrim(enabled: Boolean, intro: Int, outro: Int, mode: String) {
        store.transaction { state ->
            val settings = state.optJSONObject("settings") ?: org.json.JSONObject().also { state.put("settings", it) }
            val playback = settings.optJSONObject("playback") ?: org.json.JSONObject().also { settings.put("playback", it) }
            val head = intro.coerceIn(0, MAX_SKIP_PERMILLE)
            val tail = outro.coerceIn(0, MAX_SKIP_PERMILLE)
            playback.put("skipTrim", enabled)
            playback.put(
                "skipMode",
                if (mode == teikengstudio.xmpro.player.SmartSkip.MODE_MANUAL) {
                    teikengstudio.xmpro.player.SmartSkip.MODE_MANUAL
                } else {
                    teikengstudio.xmpro.player.SmartSkip.MODE_SMART
                },
            )
            // Never trim past each other: at least a fifth of the track remains.
            playback.put("introPermille", minOf(head, MAX_SKIP_PERMILLE - tail))
            playback.put("outroPermille", minOf(tail, MAX_SKIP_PERMILLE - head))
        }
    }

    /** Home channel order and the channels the user hid, as stored. */
    fun channelPreferences(): Pair<List<Int>, Set<Int>> {
        val node = store.state().optJSONObject("settings")?.optJSONObject("homeChannels")
        val order = buildList {
            val array = node?.optJSONArray("order") ?: return@buildList
            for (index in 0 until array.length()) {
                val id = array.optInt(index, 0)
                if (id != 0) add(id)
            }
        }
        val hidden = buildSet {
            val array = node?.optJSONArray("hidden") ?: return@buildSet
            for (index in 0 until array.length()) {
                val id = array.optInt(index, 0)
                if (id != 0) add(id)
            }
        }
        return order to hidden
    }

    fun setChannelPreferences(order: List<Int>, hidden: Set<Int>) {
        store.transaction { state ->
            val settings = state.optJSONObject("settings")
                ?: org.json.JSONObject().also { state.put("settings", it) }
            val node = org.json.JSONObject()
            node.put("order", org.json.JSONArray(order))
            node.put("hidden", org.json.JSONArray(hidden.toList()))
            settings.put("homeChannels", node)
        }
    }

    fun close() {
        store.close()
    }

    // ------------------------------------------------------------------ backup

    /** Snapshot used by the backup exporter. */
    fun storageState(): org.json.JSONObject = store.state()

    fun credentialIdentities(): List<String> = credentials.identities()

    fun credentialsFor(identity: String): teikengstudio.xmpro.model.Credentials? = credentials.load(identity)

    fun saveCredentials(value: teikengstudio.xmpro.model.Credentials) = credentials.save(value)

    /** Replaces the whole state tree with a validated backup payload. */
    fun replaceState(next: org.json.JSONObject) {
        store.transaction { current ->
            val keys = current.keys().asSequence().toList()
            for (key in keys) current.remove(key)
            for (key in next.keys()) current.put(key, next.get(key))
        }
        loadActiveAccount()
    }

    fun loadActiveAccount() {
        activeAccount = store.state().optString("activeAccountId").takeIf { it.isNotEmpty() && credentials.load(it) != null }
    }
}
