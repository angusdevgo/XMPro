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

/** Codec label for a resolved stream, e.g. m4a_128 -> "AAC 128k". */
fun formatQuality(type: String): String = when (type.lowercase()) {
    "m4a_128" -> "AAC 128k"
    "m4a_64" -> "AAC 64k"
    "m4a_24" -> "AAC 24k"
    "mp3_64" -> "MP3 64k"
    "mp3_32" -> "MP3 32k"
    else -> ""
}
