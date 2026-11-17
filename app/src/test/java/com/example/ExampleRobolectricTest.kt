package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.ClipForgeDatabase
import com.example.data.local.TrackedChannelEntity
import com.example.data.network.YouTubeFeedClient
import android.media.MediaCodec
import android.media.MediaExtractor
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import com.example.data.local.GeneratedClipEntity
import com.example.data.media.LocalVideoTrimmer
import com.example.data.media.SubtitleExporter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun `app_name string matches ClipForge`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("ClipForge", context.getString(R.string.app_name))
    }

    @Test
    fun `parseChannelRssXml parses YouTube Atom feed entries and view statistics safely`() {
        val sampleXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <feed xmlns:yt="http://www.youtube.com/xml/schemas/2015"
                  xmlns:media="http://search.yahoo.com/mrss/"
                  xmlns="http://www.w3.org/2005/Atom">
              <author><name>Test Creator</name></author>
              <entry>
                <yt:videoId>abc12345678</yt:videoId>
                <yt:channelId>UCBJycsmduvYEL83R_U4JriQ</yt:channelId>
                <title>How 9:16 Framing Boosts Retention</title>
                <link rel="alternate" href="https://www.youtube.com/watch?v=abc12345678"/>
                <published>2026-11-16T12:00:00+00:00</published>
                <media:group>
                  <media:thumbnail url="https://i.ytimg.com/vi/abc12345678/hqdefault.jpg"/>
                  <media:community>
                    <media:starRating count="1200" average="4.92" min="1" max="5"/>
                    <media:statistics views="48500"/>
                  </media:community>
                </media:group>
              </entry>
            </feed>
        """.trimIndent()

        val items = YouTubeFeedClient.parseChannelRssXml(sampleXml, "UCBJycsmduvYEL83R_U4JriQ")
        assertEquals(1, items.size)
        assertEquals("abc12345678", items.first().videoId)
        assertEquals("Test Creator", items.first().channelName)
        assertEquals("How 9:16 Framing Boosts Retention", items.first().title)
        assertEquals(48_500L, items.first().viewCount)
        assertEquals(4.92, items.first().starRating, 0.01)
        assertTrue(items.first().thumbnailUrl.contains("abc12345678"))
    }

    @Test
    fun `room database persists and observes tracked channels`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, ClipForgeDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val dao = db.trackedChannelDao()
            dao.upsert(
                TrackedChannelEntity(
                    channelId = "UC_x5XG1OV2P6uZZ5FSM9Ttw",
                    handle = "@GoogleDevelopers",
                    displayName = "Google for Developers",
                    niche = "Tech",
                    healthStatus = "DOING_GREAT",
                    avgViewsPerVideo = 55_000L,
                    totalRecentViews = 550_000L,
                    avgStarRating = 4.91,
                    recentVideoCount = 10
                )
            )
            val list = dao.observeAll().first()
            assertEquals(1, list.size)
            assertEquals("Google for Developers", list.first().displayName)
            assertEquals(55_000L, list.first().avgViewsPerVideo)
        } finally {
            db.close()
        }
    }

    @Test
    fun `trimmer sample flags map cleanly to MediaCodec flags and subtitles generate SRT`() {
        val mapped = LocalVideoTrimmer.mapSampleFlagsToCodecFlags(
            MediaExtractor.SAMPLE_FLAG_SYNC or MediaExtractor.SAMPLE_FLAG_PARTIAL_FRAME
        )
        assertTrue((mapped and MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0)
        assertTrue((mapped and MediaCodec.BUFFER_FLAG_PARTIAL_FRAME) != 0)

        val srt = SubtitleExporter.generateSrt("Hook viewers fast in three seconds", durationSec = 10)
        assertTrue(srt.contains("00:00:00,000 -->"))
        assertTrue(srt.contains("Hook"))
    }

    @Test
    fun `cuj empty real workspace, dark mode toggle, About Made by Google dialog, and Sample AI notice`() {
        composeRule.waitForIdle()

        // Dashboard renders cleanly with empty real workspace (no demo data)
        composeRule.onNodeWithTag("dashboard_screen").assertIsDisplayed()
        composeRule.onNodeWithText("No channels tracked yet").assertIsDisplayed()

        // Toggle theme button works cleanly without crash
        composeRule.onNodeWithTag("theme_toggle_button").performClick()
        composeRule.waitForIdle()

        // Open About dialog and verify "Made by Google" attribution
        composeRule.onNodeWithTag("about_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("about_made_by_google_text").assertIsDisplayed()
        composeRule.onNodeWithText("Made by Google").assertIsDisplayed()
        composeRule.onNodeWithTag("about_close_button").performClick()
        composeRule.waitForIdle()

        // Navigate across Tracker, Studio, Niche Intel, and Exports
        composeRule.onNodeWithTag("nav_tracker").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("tracker_screen").assertIsDisplayed()

        composeRule.onNodeWithTag("nav_studio").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("studio_screen").assertIsDisplayed()

        composeRule.onNodeWithTag("nav_trends").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("niche_intel_screen").assertIsDisplayed()

        // Trigger optional Sample AI action and confirm Firebase notice banner appears
        composeRule.onNodeWithTag("mine_niche_button").performClick()
        composeRule.mainClock.advanceTimeBy(300L)
        composeRule.waitForIdle()
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            composeRule.onAllNodesWithTag("ai_firebase_notice_banner")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("ai_firebase_notice_banner").assertIsDisplayed()

        composeRule.onNodeWithTag("nav_queue").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("queue_screen").assertIsDisplayed()
    }

    @Test
    fun `studio displays preview player and MP4 save or share actions when clip is exported`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val tempMp4 = File(context.cacheDir, "test_preview_clip.mp4").apply {
            writeBytes(ByteArray(128) { 1 })
        }
        val db = ClipForgeDatabase.getInstance(context)
        var insertedId = 0L
        try {
            insertedId = runBlocking {
                db.generatedClipDao().insert(
                    GeneratedClipEntity(
                        sourceTitle = "Source Video",
                        sourceUrl = "https://www.youtube.com/watch?v=abc12345678",
                        localVideoUri = null,
                        exportedMp4Path = tempMp4.absolutePath,
                        thumbnailUrl = null,
                        clipTitle = "Viral Hook Cut",
                        seoDescription = "Clip description",
                        hashtags = "#Shorts",
                        startSec = 0,
                        endSec = 15,
                        viralityScore = 88,
                        hookStrength = 88,
                        completeness = 90,
                        emotionalPayoff = 82,
                        framingMode = "SMART_CENTER",
                        subtitleStyle = "HORMOZI",
                        loudnessNormalized = true,
                        silenceTrimmed = true,
                        transcriptSnippet = "Watch how fast this works",
                        stickerText = "VIRAL HOOK",
                        uploadStatus = "EXPORTED",
                        scheduledLabel = "MP4 Ready",
                        isSampleAiOutput = false
                    )
                )
            }

            composeRule.onNodeWithTag("nav_studio").performClick()
            composeRule.waitForIdle()
            composeRule.waitUntil(timeoutMillis = 5_000L) {
                runCatching {
                    composeRule.onNodeWithTag("studio_screen").performScrollToIndex(1)
                    true
                }.getOrDefault(false)
            }
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("preview_play_pause_button").performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithTag("re_export_mp4_button").performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithTag("save_mp4_device_button").performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithTag("share_upload_mp4_button").performScrollTo().assertIsDisplayed()
        } finally {
            if (insertedId != 0L) {
                runBlocking { db.generatedClipDao().deleteById(insertedId) }
            }
            tempMp4.delete()
        }
    }
}
