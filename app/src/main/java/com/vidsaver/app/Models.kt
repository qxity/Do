package com.vidsaver.app

import java.net.URI

enum class Quality(val label: String, val height: Int?, val audioFormat: String?) {
    BEST("Terbaik", null, null),
    P1080("1080p", 1080, null),
    P720("720p", 720, null),
    P480("480p", 480, null),
    P360("360p", 360, null),
    MP3("Audio MP3", null, "mp3"),
    M4A("Audio M4A", null, "m4a");

    val isAudio: Boolean get() = audioFormat != null
}

enum class Status { QUEUED, RUNNING, PROCESSING, DONE, FAILED, CANCELLED }

enum class Platform(val label: String) {
    YOUTUBE("YouTube"), TIKTOK("TikTok"), INSTAGRAM("Instagram"),
    FACEBOOK("Facebook"), X("X / Twitter"), OTHER("Situs lain");

    companion object {
        fun of(url: String): Platform {
            val host = runCatching { URI(url.trim()).host }.getOrNull()?.lowercase() ?: return OTHER
            fun match(vararg domains: String) = domains.any { host == it || host.endsWith(".$it") }
            return when {
                match("youtube.com", "youtu.be") -> YOUTUBE
                match("tiktok.com") -> TIKTOK
                match("instagram.com", "instagr.am") -> INSTAGRAM
                match("facebook.com", "fb.watch", "fb.com") -> FACEBOOK
                match("twitter.com", "x.com") -> X
                else -> OTHER
            }
        }
    }
}

data class DownloadTask(
    val id: String,
    val url: String,
    val title: String,
    val thumbnail: String?,
    val quality: Quality,
    val platform: Platform,
    val progress: Float = 0f,
    val etaSec: Long = -1,
    val speed: String = "",
    val status: Status = Status.QUEUED,
    val error: String? = null,
    val savedUris: List<String> = emptyList(),
    val savedMime: String? = null,
) {
    val isActive: Boolean
        get() = status == Status.QUEUED || status == Status.RUNNING || status == Status.PROCESSING
}

data class HistoryItem(
    val id: String,
    val title: String,
    val url: String,
    val thumbnail: String?,
    val uri: String,
    val mime: String,
    val platform: Platform,
    val time: Long,
)

data class VideoPreview(
    val url: String,
    val title: String,
    val thumbnail: String?,
    val uploader: String?,
    val duration: Int,
    val platform: Platform,
)

object UrlUtil {
    private val regex = Regex("""https?://[^\s"'<>]+""")
    fun extract(text: String?): String? =
        text?.let { regex.find(it)?.value?.trimEnd('.', ',', ')', ']', '!', '?') }
}

fun formatDuration(sec: Long): String {
    if (sec <= 0) return ""
    val h = sec / 3600
    val m = (sec % 3600) / 60
    val s = sec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
