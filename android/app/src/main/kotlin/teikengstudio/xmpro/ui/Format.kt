package teikengstudio.xmpro.ui

/** Listening time in hours and minutes; never a bare minute count. */
fun formatListeningTime(seconds: Int): String {
    if (seconds <= 0) return "0 分"
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    return when {
        hours > 0 && minutes > 0 -> "$hours 小时 $minutes 分"
        hours > 0 -> "$hours 小时"
        else -> "$minutes 分"
    }
}
