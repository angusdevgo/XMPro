package teikengstudio.xmpro.model

/** Ximalaya account profile shown in settings; cached as non-secret data. */
class UserProfile(
    val uid: String,
    val nickname: String = "",
    val avatarUrl: String? = null,
    val intro: String = "",
)
