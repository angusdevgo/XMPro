package teikengstudio.xmpro.model

/** Ximalaya account profile shown in settings; cached as non-secret data. */
class UserProfile(
    val uid: String,
    val nickname: String = "",
    val avatarUrl: String? = null,
    val intro: String = "",
    /** null when the endpoint does not expose the counter. */
    val followers: Int? = null,
    val following: Int? = null,
    val subscriptions: Int? = null,
)

/** Public counters from `revision/user`; 0 when the account has none. */
data class SocialCounts(
    val following: Int = 0,
    val followers: Int = 0,
    val subscriptions: Int = 0,
    val albums: Int = 0,
    val tracks: Int = 0,
)
