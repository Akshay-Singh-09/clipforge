package com.example

import android.media.MediaCodec
import android.media.MediaExtractor
import com.example.data.ClipForgeRepository
import com.example.data.ai.PlaceholderAiRepository
import com.example.data.local.IngestedVideoEntity
import com.example.data.local.TrackedChannelEntity
import com.example.data.media.LocalVideoTrimmer
import com.example.data.media.SubtitleExporter
import com.example.data.network.YouTubeFeedClient
import com.example.model.DurationWindow
import com.example.model.FramingMode
import com.example.model.SubtitleStyle
import com.example.model.UploadStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun `extractChannelId parses raw id and full channel url`() {
        assertEquals(
            "UCBJycsmduvYEL83R_U4JriQ",
            YouTubeFeedClient.extractChannelId("UCBJycsmduvYEL83R_U4JriQ")
        )
        assertEquals(
            "UCBJycsmduvYEL83R_U4JriQ",
            YouTubeFeedClient.extractChannelId("https://www.youtube.com/channel/UCBJycsmduvYEL83R_U4JriQ")
        )
        assertNull(YouTubeFeedClient.extractChannelId("@mkbhd"))
    }

    @Test
    fun `extractChannelIdFromHtml resolves channel id from rss link, json, or canonical url`() {
        val rssHtml = """<link rel="alternate" type="application/rss+xml" href="https://www.youtube.com/feeds/videos.xml?channel_id=UCBJycsmduvYEL83R_U4JriQ">"""
        assertEquals("UCBJycsmduvYEL83R_U4JriQ", YouTubeFeedClient.extractChannelIdFromHtml(rssHtml))

        val jsonHtml = """{"externalId":"UC_x5XG1OV2P6uZZ5FSM9Ttw"}"""
        assertEquals("UC_x5XG1OV2P6uZZ5FSM9Ttw", YouTubeFeedClient.extractChannelIdFromHtml(jsonHtml))

        val canonicalHtml = """<link rel="canonical" href="https://www.youtube.com/channel/UCVHFbw7woebKtffv6h3H0gA">"""
        assertEquals("UCVHFbw7woebKtffv6h3H0gA", YouTubeFeedClient.extractChannelIdFromHtml(canonicalHtml))
    }

    @Test
    fun `mapSampleFlagsToCodecFlags converts MediaExtractor flags to MediaCodec flags`() {
        val mapped = LocalVideoTrimmer.mapSampleFlagsToCodecFlags(
            MediaExtractor.SAMPLE_FLAG_SYNC or MediaExtractor.SAMPLE_FLAG_PARTIAL_FRAME
        )
        assertTrue((mapped and MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0)
        assertTrue((mapped and MediaCodec.BUFFER_FLAG_PARTIAL_FRAME) != 0)
        assertEquals(0, LocalVideoTrimmer.mapSampleFlagsToCodecFlags(0))
    }

    @Test
    fun `extractYouTubeVideoId supports watch, youtu_be, shorts, and bare ids`() {
        assertEquals("dQw4w9WgXcQ", YouTubeFeedClient.extractYouTubeVideoId("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertEquals("dQw4w9WgXcQ", YouTubeFeedClient.extractYouTubeVideoId("https://youtu.be/dQw4w9WgXcQ?t=12"))
        assertEquals("dQw4w9WgXcQ", YouTubeFeedClient.extractYouTubeVideoId("https://youtube.com/shorts/dQw4w9WgXcQ"))
        assertEquals("dQw4w9WgXcQ", YouTubeFeedClient.extractYouTubeVideoId("dQw4w9WgXcQ"))
        assertNull(YouTubeFeedClient.extractYouTubeVideoId("https://example.com/video"))
    }

    @Test
    fun `SubtitleExporter generates valid SRT and ASS karaoke scripts`() {
        val srt = SubtitleExporter.generateSrt("Hook viewers fast in three seconds flat", 12)
        assertTrue(srt.contains("00:00:00,000 -->"))
        assertTrue(srt.contains("Hook viewers fast in"))

        val ass = SubtitleExporter.generateAssKaraoke("Hook viewers fast", 9, SubtitleStyle.HORMOZI.name)
        assertTrue(ass.contains("[Script Info]"))
        assertTrue(ass.contains("{\\k"))
        assertTrue(ass.contains("PlayResX: 1080"))
    }

    @Test
    fun `computeLiveNicheAnalytics extracts real keyword density and average views`() {
        val channels = listOf(
            TrackedChannelEntity(
                channelId = "UC1234567890123456789012",
                handle = "@tech",
                displayName = "Tech Creator",
                niche = "Tech",
                healthStatus = "DOING_GREAT",
                avgViewsPerVideo = 100_000L,
                totalRecentViews = 200_000L,
                avgStarRating = 4.9,
                recentVideoCount = 2
            )
        )
        val videos = listOf(
            IngestedVideoEntity(
                videoId = "vid1",
                channelId = "UC1234567890123456789012",
                channelName = "Tech Creator",
                title = "Why 5 Android Phones Failed Battery Tests?",
                videoUrl = "https://youtube.com/watch?v=vid1",
                thumbnailUrl = "",
                publishedIso = "2026-11-16",
                viewCount = 120_000L,
                starRating = 4.95
            ),
            IngestedVideoEntity(
                videoId = "vid2",
                channelId = "UC1234567890123456789012",
                channelName = "Tech Creator",
                title = "Best Android Camera Battery Comparison",
                videoUrl = "https://youtube.com/watch?v=vid2",
                thumbnailUrl = "",
                publishedIso = "2026-11-15",
                viewCount = 80_000L,
                starRating = 4.85
            )
        )

        val analytics = ClipForgeRepository.computeLiveNicheAnalytics(channels, videos)
        assertEquals(1, analytics.size)
        assertEquals("Tech", analytics.first().niche)
        assertEquals(100_000L, analytics.first().avgViews)
        assertTrue(analytics.first().topKeywords.contains("#android"))
        assertTrue(analytics.first().topKeywords.contains("#battery"))
    }

    @Test
    fun `computeDeterministicHookScore rewards strong hooks and short duration`() {
        val strong = ClipForgeRepository.computeDeterministicHookScore("Why 3 Tricks Boost Retention?", 30)
        val plain = ClipForgeRepository.computeDeterministicHookScore("Test", 120)
        assertTrue(strong > plain)
    }

    @Test
    fun `PlaceholderAiRepository labels outputs as sample and marks Firebase unconfigured`() = runTest {
        val repo = PlaceholderAiRepository()
        assertFalse(repo.isFirebaseConnected)

        val result = repo.analyzeVideoAndGenerateClips(
            sourceTitle = "Camera Test",
            sourceUrl = "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            localVideoUri = null,
            thumbnailUrl = null,
            clipCount = 3,
            durationWindow = DurationWindow.MEDIUM_30_60,
            framingMode = FramingMode.SMART_CENTER,
            subtitleStyle = SubtitleStyle.HORMOZI,
            loudnessNormalized = true,
            silenceTrimmed = true
        )

        assertTrue(result.isSampleOutput)
        assertTrue(result.noticeMessage.contains("Firebase", ignoreCase = true))
        assertEquals(3, result.clips.size)
        assertTrue(result.clips.all { it.isSampleAiOutput && it.clipTitle.contains("[Sample AI]") })
    }

    @Test
    fun `UploadStatus cycles cleanly across lifecycle`() {
        assertEquals(UploadStatus.SCHEDULED, UploadStatus.DRAFT.next())
        assertEquals(UploadStatus.EXPORTED, UploadStatus.SCHEDULED.next())
        assertEquals(UploadStatus.PUBLISHED, UploadStatus.EXPORTED.next())
        assertEquals(UploadStatus.DRAFT, UploadStatus.PUBLISHED.next())
    }
}
