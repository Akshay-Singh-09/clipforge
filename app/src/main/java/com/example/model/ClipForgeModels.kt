package com.example.model

enum class ThemeMode(val label: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark")
}

enum class ChannelHealthStatus(val label: String) {
    DOING_GREAT("Doing Great"),
    STABLE("Stable"),
    NEEDS_ATTENTION("Needs Attention");

    companion object {
        fun fromRaw(raw: String): ChannelHealthStatus =
            entries.firstOrNull { it.name == raw } ?: STABLE
    }
}

enum class FramingMode(val label: String, val shortDesc: String) {
    SMART_CENTER("Smart Center", "Single active speaker auto-crop (9:16)"),
    SPLIT_SCREEN("2-Speaker Split", "Stacked dual-camera podcast framing"),
    BLURRED_LETTERBOX("Blur Letterbox", "Full 16:9 frame over ambient blur");

    companion object {
        fun fromRaw(raw: String): FramingMode =
            entries.firstOrNull { it.name == raw } ?: SMART_CENTER
    }
}

enum class SubtitleStyle(val label: String, val accentHex: Long) {
    HORMOZI("Hormozi Pop", 0xFFFACC15),
    NEON("Neon Pulse", 0xFF38BDF8),
    CLEAN("Clean Minimal", 0xFFFFFFFF),
    BOLD("Bold Impact", 0xFFFF5C67);

    companion object {
        fun fromRaw(raw: String): SubtitleStyle =
            entries.firstOrNull { it.name == raw } ?: HORMOZI
    }
}

enum class DurationWindow(val label: String, val minSec: Int, val maxSec: Int) {
    SHORT_15_30("15–30s", 15, 30),
    MEDIUM_30_60("30–60s", 30, 60),
    LONG_60_90("60–90s", 60, 90)
}

enum class UploadStatus(val label: String) {
    DRAFT("Draft"),
    SCHEDULED("Scheduled"),
    EXPORTED("Exported MP4"),
    PUBLISHED("Published");

    fun next(): UploadStatus = when (this) {
        DRAFT -> SCHEDULED
        SCHEDULED -> EXPORTED
        EXPORTED -> PUBLISHED
        PUBLISHED -> DRAFT
    }

    companion object {
        fun fromRaw(raw: String): UploadStatus =
            entries.firstOrNull { it.name == raw } ?: DRAFT
    }
}

data class RssVideoItem(
    val videoId: String,
    val channelId: String,
    val channelName: String,
    val title: String,
    val videoUrl: String,
    val thumbnailUrl: String,
    val publishedIso: String,
    val viewCount: Long = 0L,
    val starRating: Double = 0.0,
    val queuedToStudio: Boolean = false
)

data class ResolvedChannelInfo(
    val channelId: String,
    val displayName: String,
    val handle: String
)

data class VideoOEmbedInfo(
    val videoUrl: String,
    val title: String,
    val authorName: String,
    val thumbnailUrl: String
)

data class LocalVideoMeta(
    val uriString: String,
    val displayName: String,
    val durationMs: Long,
    val width: Int,
    val height: Int
)

data class DashboardSummary(
    val totalViews: Long,
    val trackedChannelCount: Int,
    val ingestedVideoCount: Int,
    val exportedClipCount: Int,
    val avgRating: Double,
    val viewSeries: List<Float>,
    val doingGreatCount: Int,
    val stableCount: Int,
    val needsAttentionCount: Int
)

data class NicheLiveAnalytics(
    val niche: String,
    val videoCount: Int,
    val avgTitleWords: Double,
    val avgViews: Long,
    val topKeywords: List<String>,
    val topPatternSummary: String
)

sealed interface AsyncStatus<out T> {
    data object Idle : AsyncStatus<Nothing>
    data object Loading : AsyncStatus<Nothing>
    data class Success<T>(val data: T) : AsyncStatus<T>
    data class Error(val message: String) : AsyncStatus<Nothing>
}
