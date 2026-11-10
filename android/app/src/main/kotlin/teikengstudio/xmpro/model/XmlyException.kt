package teikengstudio.xmpro.model

/** Protocol or storage failure with a stable code the UI can branch on. */
class XmlyException(
    val code: String,
    override val message: String,
    val retryAfter: java.time.Duration? = null,
    val definitive: Boolean = false,
) : Exception(message)

/** Thrown when a cooperative operation was cancelled by the caller. */
class DownloadStopped : Exception()
