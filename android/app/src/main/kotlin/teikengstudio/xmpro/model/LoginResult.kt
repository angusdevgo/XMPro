package teikengstudio.xmpro.model

/**
 * Everything a successful SMS login returns: the credentials plus whatever
 * profile fields the passport service hands back with the token.
 */
class LoginResult(
    val credentials: Credentials,
    val nickname: String = "",
    val avatarUrl: String? = null,
    val mobile: String = "",
)
