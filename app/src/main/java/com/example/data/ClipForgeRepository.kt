package com.example.data

import android.net.Uri
import androidx.core.net.toUri
import com.example.data.ai.AiMetadataResult
import com.example.data.ai.AiRepository
import com.example.data.local.ClipForgeDatabase
import com.example.data.local.GeneratedClipEntity
import com.example.data.local.IngestedVideoEntity
import com.example.data.local.NichePlaybookEntity
import com.example.data.local.TrackedChannelEntity
import com.example.data.media.LocalVideoTrimmer
import com.example.data.network.YouTubeFeedClient
import com.example.model.ChannelHealthStatus
import com.example.model.FramingMode
import com.example.model.NicheLiveAnalytics
import com.example.model.RssVideoItem
import com.example.model.SubtitleStyle
import com.example.model.UploadStatus
import com.example.model.VideoOEmbedInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import java.io.File

class ClipForgeRepository(
    private val db: ClipForgeDatabase,
    private val feedClient: YouTubeFeedClient,
    private val aiRepository: AiRepository,
    private val trimmer: LocalVideoTrimmer? = null
) {
    private val channelDao = db.trackedChannelDao()
    private val videoDao = db.ingestedVideoDao()
    private val clipDao = db.generatedClipDao()
    private val playbookDao = db.nichePlaybookDao()

    val trackedChannels: Flow<List<TrackedChannelEntity>> = channelDao.observeAll()
    val ingestedVideos: Flow<List<IngestedVideoEntity>> = videoDao.observeAll()
    val generatedClips: Flow<List<GeneratedClipEntity>> = clipDao.observeAll()
    val nichePlaybooks: Flow<List<NichePlaybookEntity>> = playbookDao.observeAll()

    val isFirebaseAiConnected: Boolean
        get() = aiRepository.isFirebaseConnected

    suspend fun addAndSyncChannel(
        channelInput: String,
        customDisplayName: String,
        niche: String
    ): Result<TrackedChannelEntity> {
        val resolved = feedClient.resolveChannelInput(channelInput)
            .getOrElse { return Result.failure(it) }

        val rssResult = feedClient.fetchChannelRssFeed(resolved.channelId)
        val videos = rssResult.getOrElse { return Result.failure(it) }

        val firstAuthor = videos.firstOrNull()?.channelName.orEmpty()
        val displayName = customDisplayName.trim()
            .ifBlank { resolved.displayName }
            .ifBlank { firstAuthor }
            .ifBlank { resolved.channelId }

        val handle = resolved.handle.ifBlank {
            "@${displayName.lowercase().replace(Regex("[^a-z0-9]"), "")}"
        }

        val metrics = computeChannelMetrics(videos)
        val entity = TrackedChannelEntity(
            channelId = resolved.channelId,
            handle = handle,
            displayName = displayName,
            niche = niche.ifBlank { "Tech" },
            healthStatus = metrics.health.name,
            avgViewsPerVideo = metrics.avgViews,
            totalRecentViews = metrics.totalViews,
            avgStarRating = metrics.avgRating,
            recentVideoCount = videos.size,
            autoMonitor = true
        )
        val rowId = channelDao.upsert(entity)
        saveVideos(videos)
        return Result.success(entity.copy(id = rowId))
    }

    suspend fun toggleChannelMonitor(channelId: Long, enabled: Boolean) {
        channelDao.setAutoMonitor(channelId, enabled)
    }

    suspend fun deleteChannel(channel: TrackedChannelEntity) {
        videoDao.deleteForChannel(channel.channelId)
        channelDao.deleteById(channel.id)
    }

    suspend fun syncChannelRss(channelId: String): Result<List<RssVideoItem>> {
        val result = feedClient.fetchChannelRssFeed(channelId)
        result.onSuccess { items ->
            saveVideos(items)
            val metrics = computeChannelMetrics(items)
            val resolvedName = items.firstOrNull()?.channelName?.ifBlank { channelId } ?: channelId
            channelDao.updateLiveMetrics(
                channelId = channelId,
                displayName = resolvedName,
                healthStatus = metrics.health.name,
                avgViews = metrics.avgViews,
                totalViews = metrics.totalViews,
                avgRating = metrics.avgRating,
                videoCount = items.size,
                timestamp = System.currentTimeMillis()
            )
        }
        return result
    }

    suspend fun syncAllTrackedChannels(): Result<Int> {
        val channels = channelDao.getAllOnce().filter { it.autoMonitor }
        if (channels.isEmpty()) {
            return Result.failure(IllegalStateException("Add at least one YouTube channel first"))
        }
        var syncedVideos = 0
        var lastError: Throwable? = null
        for (channel in channels) {
            val res = syncChannelRss(channel.channelId)
            res.onSuccess { list -> syncedVideos += list.size }
            res.onFailure { err -> lastError = err }
        }
        return if (syncedVideos > 0 || lastError == null) {
            Result.success(syncedVideos)
        } else {
            Result.failure(lastError ?: IllegalStateException("RSS sync failed"))
        }
    }

    private suspend fun saveVideos(items: List<RssVideoItem>) {
        if (items.isEmpty()) return
        val entities = items.take(10).map { item ->
            IngestedVideoEntity(
                videoId = item.videoId,
                channelId = item.channelId,
                channelName = item.channelName,
                title = item.title,
                videoUrl = item.videoUrl,
                thumbnailUrl = item.thumbnailUrl,
                publishedIso = item.publishedIso,
                viewCount = item.viewCount,
                starRating = item.starRating
            )
        }
        videoDao.upsertAll(entities)
    }

    suspend fun markVideoQueued(videoId: String) {
        videoDao.markQueued(videoId, true)
    }

    suspend fun fetchYouTubeOEmbed(url: String): Result<VideoOEmbedInfo> =
        feedClient.fetchVideoOEmbed(url)

    suspend fun createAndTrimClip(
        sourceTitle: String,
        sourceUrl: String,
        localVideoUri: String?,
        thumbnailUrl: String?,
        clipTitle: String,
        captionScript: String,
        stickerText: String,
        startSec: Int,
        endSec: Int,
        framingMode: FramingMode,
        subtitleStyle: SubtitleStyle,
        loudnessNormalized: Boolean,
        silenceTrimmed: Boolean
    ): Result<GeneratedClipEntity> {
        return try {
            val safeStart = startSec.coerceAtLeast(0)
            val safeEnd = endSec.coerceAtLeast(safeStart + 5)
            val cleanTitle = clipTitle.trim().ifBlank { sourceTitle.ifBlank { "Untitled Vertical Clip" } }
            val cleanScript = captionScript.trim().ifBlank { cleanTitle }
            val cleanSticker = stickerText.trim()

            var exportedFile: File? = null
            if (trimmer != null) {
                exportedFile = if (!localVideoUri.isNullOrBlank()) {
                    trimmer.trimToMp4(localVideoUri.toUri(), safeStart, safeEnd)
                        .getOrElse { return Result.failure(it) }
                } else {
                    trimmer.renderVerticalHighlightMp4(
                        title = cleanTitle,
                        transcript = cleanScript,
                        stickerText = cleanSticker,
                        framingMode = framingMode,
                        subtitleStyle = subtitleStyle,
                        durationSec = safeEnd - safeStart,
                        thumbnailUrl = thumbnailUrl
                    ).getOrElse { return Result.failure(it) }
                }
            }

            val entity = GeneratedClipEntity(
                sourceTitle = sourceTitle.ifBlank { cleanTitle },
                sourceUrl = sourceUrl,
                localVideoUri = localVideoUri,
                exportedMp4Path = exportedFile?.absolutePath,
                thumbnailUrl = thumbnailUrl,
                clipTitle = cleanTitle,
                seoDescription = buildAutoDescription(cleanTitle, sourceUrl),
                hashtags = extractHashtags(cleanTitle),
                startSec = safeStart,
                endSec = safeEnd,
                viralityScore = computeDeterministicHookScore(cleanTitle, safeEnd - safeStart),
                hookStrength = computeDeterministicHookScore(cleanTitle, 15),
                completeness = if ((safeEnd - safeStart) in 15..60) 90 else 76,
                emotionalPayoff = if (cleanTitle.contains("?") || cleanTitle.contains("!")) 88 else 78,
                framingMode = framingMode.name,
                subtitleStyle = subtitleStyle.name,
                loudnessNormalized = loudnessNormalized,
                silenceTrimmed = silenceTrimmed,
                transcriptSnippet = cleanScript,
                stickerText = cleanSticker,
                uploadStatus = if (exportedFile != null) UploadStatus.EXPORTED.name else UploadStatus.DRAFT.name,
                scheduledLabel = if (exportedFile != null) "MP4 Ready" else "Draft",
                isSampleAiOutput = false
            )
            val id = clipDao.insert(entity)
            Result.success(entity.copy(id = id))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun reExportClipMp4(clip: GeneratedClipEntity): Result<GeneratedClipEntity> {
        return try {
            val activeTrimmer = trimmer
                ?: return Result.failure(IllegalStateException("Video exporter unavailable"))
            val safeStart = clip.startSec.coerceAtLeast(0)
            val safeEnd = clip.endSec.coerceAtLeast(safeStart + 5)
            val framing = FramingMode.fromRaw(clip.framingMode)
            val style = SubtitleStyle.fromRaw(clip.subtitleStyle)

            val newFile = if (!clip.localVideoUri.isNullOrBlank()) {
                activeTrimmer.trimToMp4(clip.localVideoUri.toUri(), safeStart, safeEnd)
                    .getOrElse { return Result.failure(it) }
            } else {
                activeTrimmer.renderVerticalHighlightMp4(
                    title = clip.clipTitle,
                    transcript = clip.transcriptSnippet,
                    stickerText = clip.stickerText,
                    framingMode = framing,
                    subtitleStyle = style,
                    durationSec = safeEnd - safeStart,
                    thumbnailUrl = clip.thumbnailUrl
                ).getOrElse { return Result.failure(it) }
            }

            clip.exportedMp4Path?.let { oldPath ->
                if (oldPath != newFile.absolutePath) {
                    try {
                        File(oldPath).takeIf { it.exists() }?.delete()
                    } catch (_: Exception) {
                    }
                }
            }

            val updated = clip.copy(
                exportedMp4Path = newFile.absolutePath,
                startSec = safeStart,
                endSec = safeEnd,
                viralityScore = computeDeterministicHookScore(clip.clipTitle, safeEnd - safeStart),
                hookStrength = computeDeterministicHookScore(clip.clipTitle, 15),
                completeness = if ((safeEnd - safeStart) in 15..60) 90 else 76,
                emotionalPayoff = if (clip.clipTitle.contains("?") || clip.clipTitle.contains("!")) 88 else 78,
                uploadStatus = UploadStatus.EXPORTED.name,
                scheduledLabel = "MP4 Ready"
            )
            clipDao.update(updated)
            Result.success(updated)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun attachLocalVideoToClip(
        clip: GeneratedClipEntity,
        localUri: Uri
    ): Result<GeneratedClipEntity> {
        val withUri = clip.copy(localVideoUri = localUri.toString())
        return reExportClipMp4(withUri)
    }

    suspend fun updateClip(clip: GeneratedClipEntity) {
        clipDao.update(clip)
    }

    suspend fun updateClipUploadStatus(clipId: Long, status: UploadStatus, scheduledLabel: String) {
        clipDao.updateUploadState(clipId, status.name, scheduledLabel)
    }

    suspend fun deleteClip(clipId: Long) {
        val existing = clipDao.getAllOnce().firstOrNull { it.id == clipId }
        existing?.exportedMp4Path?.let { path ->
            try {
                File(path).takeIf { it.exists() }?.delete()
            } catch (_: Exception) {
            }
        }
        clipDao.deleteById(clipId)
    }

    suspend fun runSampleAiHookScore(clip: GeneratedClipEntity): Result<AiMetadataResult> {
        return try {
            val res = aiRepository.regenerateMetadata(clip.clipTitle, clip.transcriptSnippet)
            val updated = clip.copy(
                clipTitle = res.clipTitle,
                stickerText = res.stickerText,
                seoDescription = res.seoDescription,
                hashtags = res.hashtags,
                isSampleAiOutput = res.isSampleOutput
            )
            clipDao.update(updated)
            Result.success(res)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun mineSampleAiNichePlaybook(niche: String): Result<NichePlaybookEntity> {
        return try {
            val res = aiRepository.mineNichePlaybook(niche)
            val id = playbookDao.insert(res.playbook)
            Result.success(res.playbook.copy(id = id))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        private data class ChannelMetrics(
            val avgViews: Long,
            val totalViews: Long,
            val avgRating: Double,
            val health: ChannelHealthStatus
        )

        private fun computeChannelMetrics(videos: List<RssVideoItem>): ChannelMetrics {
            if (videos.isEmpty()) {
                return ChannelMetrics(0L, 0L, 0.0, ChannelHealthStatus.STABLE)
            }
            val totalViews = videos.sumOf { it.viewCount }
            val avgViews = totalViews / videos.size
            val rated = videos.filter { it.starRating > 0.0 }
            val avgRating = if (rated.isNotEmpty()) rated.map { it.starRating }.average() else 0.0
            val health = when {
                avgViews >= 50_000L || avgRating >= 4.75 -> ChannelHealthStatus.DOING_GREAT
                avgViews >= 5_000L || videos.size >= 5 -> ChannelHealthStatus.STABLE
                else -> ChannelHealthStatus.NEEDS_ATTENTION
            }
            return ChannelMetrics(avgViews, totalViews, avgRating, health)
        }

        fun computeLiveNicheAnalytics(
            channels: List<TrackedChannelEntity>,
            videos: List<IngestedVideoEntity>
        ): List<NicheLiveAnalytics> {
            if (videos.isEmpty()) return emptyList()
            val channelNicheMap = channels.associate { it.channelId to it.niche }
            val stopWords = setOf(
                "the", "and", "for", "that", "this", "with", "from", "your", "what", "when",
                "about", "into", "have", "you", "are", "was", "were", "how", "why", "will"
            )
            return videos.groupBy { channelNicheMap[it.channelId] ?: "General" }
                .map { (niche, nicheVideos) ->
                    val avgWords = nicheVideos.map { it.title.trim().split(Regex("\\s+")).size }.average()
                    val avgViews = nicheVideos.sumOf { it.viewCount } / nicheVideos.size.coerceAtLeast(1)
                    val keywords = nicheVideos
                        .flatMap { it.title.lowercase().split(Regex("[^a-z0-9]+")) }
                        .filter { it.length >= 4 && it !in stopWords }
                        .groupingBy { it }
                        .eachCount()
                        .entries
                        .sortedByDescending { it.value }
                        .take(5)
                        .map { "#${it.key}" }
                    val questions = nicheVideos.count { it.title.contains("?") }
                    val numbers = nicheVideos.count { it.title.any { ch -> ch.isDigit() } }
                    val pattern = buildString {
                        append("${nicheVideos.size} live videos analyzed · ")
                        append("$questions question hooks, $numbers numbered titles")
                    }
                    NicheLiveAnalytics(
                        niche = niche,
                        videoCount = nicheVideos.size,
                        avgTitleWords = avgWords,
                        avgViews = avgViews,
                        topKeywords = keywords,
                        topPatternSummary = pattern
                    )
                }
                .sortedByDescending { it.avgViews }
        }

        fun computeDeterministicHookScore(title: String, durationSec: Int): Int {
            var score = 62
            val words = title.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            if (words.size in 4..10) score += 10
            if (title.contains("?") || title.contains("!")) score += 7
            if (title.any { it.isDigit() }) score += 8
            if (durationSec in 15..60) score += 9
            return score.coerceIn(45, 98)
        }

        private fun buildAutoDescription(title: String, sourceUrl: String): String {
            return if (sourceUrl.isNotBlank()) {
                "$title\n\nSource: $sourceUrl"
            } else {
                "$title\n\nExported 9:16 vertical short."
            }
        }

        private fun extractHashtags(title: String): String {
            val words = title.lowercase()
                .split(Regex("[^a-z0-9]+"))
                .filter { it.length >= 4 }
                .distinct()
                .take(4)
                .map { "#$it" }
            return (listOf("#shorts") + words).distinct().joinToString(" ")
        }
    }
}
