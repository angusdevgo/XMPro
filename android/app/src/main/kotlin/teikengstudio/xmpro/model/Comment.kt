package teikengstudio.xmpro.model

/**
 * One comment on a track.
 *
 * The web comment endpoint returns both top level comments and a single page of
 * their replies, so [replies] is always shallow: a reply's own [replies] list is
 * not fetched.
 */
class Comment(
    val id: String = "",
    val uid: String = "",
    val nickname: String = "",
    val avatarUrl: String? = null,
    val content: String = "",
    /** Pre-formatted month from the API, e.g. `2023-09`; empty when unknown. */
    val createdAt: String = "",
    val likes: Int = 0,
    val replyCount: Int = 0,
    val replies: List<Comment> = emptyList(),
) {
    val hasTime: Boolean get() = createdAt.isNotEmpty()
}

class CommentPage(
    val comments: List<Comment>,
    val page: Int,
    val totalPages: Int,
    val total: Int,
) {
    val hasMore: Boolean get() = page < totalPages
}
