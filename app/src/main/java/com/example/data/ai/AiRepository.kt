package com.example.data.ai

import com.example.data.local.GeneratedClipEntity
import com.example.data.local.NichePlaybookEntity
import com.example.model.DurationWindow
import com.example.model.FramingMode
import com.example.model.SubtitleStyle

data class AiClipGenerationResult(
    val clips: List<GeneratedClipEntity>,
    val isSampleOutput: Boolean,
    val noticeMessage: String
)

data class AiNicheInsightResult(
    val playbook: NichePlaybookEntity,
    val isSampleOutput: Boolean,
    val noticeMessage: String
)

data class AiMetadataResult(
    val clipTitle: String,
    val stickerText: String,
    val seoDescription: String,
    val hashtags: String,
    val isSampleOutput: Boolean,
    val noticeMessage: String
)

interface AiRepository {
    val isFirebaseConnected: Boolean

    suspend fun analyzeVideoAndGenerateClips(
        sourceTitle: String,
        sourceUrl: String,
        localVideoUri: String?,
        thumbnailUrl: String?,
        clipCount: Int,
        durationWindow: DurationWindow,
        framingMode: FramingMode,
        subtitleStyle: SubtitleStyle,
        loudnessNormalized: Boolean,
        silenceTrimmed: Boolean
    ): AiClipGenerationResult

    suspend fun mineNichePlaybook(niche: String): AiNicheInsightResult

    suspend fun regenerateMetadata(
        currentTitle: String,
        transcript: String
    ): AiMetadataResult
}

/**
 * Placeholder AI implementation used when Firebase AI Logic is not provisioned.
 * Clearly labels every generated item as sample output and exposes a Firebase notice.
 */
class PlaceholderAiRepository : AiRepository {
    override val isFirebaseConnected: Boolean = false

    private val notice =
        "Sample AI output shown. Enable Firebase in AI Studio to connect Gemini via Firebase AI Logic."

    override suspend fun analyzeVideoAndGenerateClips(
        sourceTitle: String,
        sourceUrl: String,
        localVideoUri: String?,
        thumbnailUrl: String?,
        clipCount: Int,
        durationWindow: DurationWindow,
        framingMode: FramingMode,
        subtitleStyle: SubtitleStyle,
        loudnessNormalized: Boolean,
        silenceTrimmed: Boolean
    ): AiClipGenerationResult {
        val safeTitle = sourceTitle.ifBlank { "Source Video Session" }
        val boundedCount = clipCount.coerceIn(1, 5)
        val span = ((durationWindow.minSec + durationWindow.maxSec) / 2).coerceIn(15, 90)

        val templates = listOf(
            Triple(
                "[Sample AI] Why 90% miss the first 3-second hook",
                "Nobody talks about the hidden retention drop at second four, and here is the exact timing fix.",
                "STOP SCROLLING"
            ),
            Triple(
                "[Sample AI] The counter-intuitive setup that doubled watch time",
                "We tested both edits side by side on mobile speakers at -14 LUFS and the retention curve flipped.",
                "2X RETENTION"
            ),
            Triple(
                "[Sample AI] What actually happens when you cut dead air",
                "Every pause over 400 milliseconds loses mobile viewers before the punchline ever lands.",
                "NO DEAD AIR"
            ),
            Triple(
                "[Sample AI] This framing shift fixes split-screen drop-off",
                "Keeping eyes anchored in the upper third of a 9:16 frame holds attention through the payoff.",
                "9:16 SECRET"
            ),
            Triple(
                "[Sample AI] The exact title structure driving 48h velocity",
                "Lead with a concrete curiosity gap, then deliver the visual proof within twenty seconds.",
                "48H SPIKE"
            )
        )

        val generated = (0 until boundedCount).map { idx ->
            val (clipHeadline, transcript, sticker) = templates[idx % templates.size]
            val start = 35 + (idx * 75)
            val end = start + span
            val baseScore = (92 - idx * 4).coerceIn(72, 96)
            GeneratedClipEntity(
                sourceTitle = safeTitle,
                sourceUrl = sourceUrl,
                localVideoUri = localVideoUri,
                thumbnailUrl = thumbnailUrl,
                clipTitle = "$clipHeadline (${safeTitle.take(22)})",
                seoDescription = "[Sample AI Output] Extracted from \"$safeTitle\". " +
                    "Requires Firebase AI Logic for live transcript hook scoring and SEO synthesis.",
                hashtags = "#shorts #creatorworkflow #editing #viralhooks",
                startSec = start,
                endSec = end,
                viralityScore = baseScore,
                hookStrength = (baseScore + 2).coerceAtMost(98),
                completeness = (baseScore - 3).coerceAtLeast(70),
                emotionalPayoff = (baseScore - 1).coerceAtLeast(72),
                framingMode = framingMode.name,
                subtitleStyle = subtitleStyle.name,
                loudnessNormalized = loudnessNormalized,
                silenceTrimmed = silenceTrimmed,
                transcriptSnippet = transcript,
                stickerText = sticker,
                uploadStatus = "DRAFT",
                scheduledLabel = "Tomorrow · ${17 + idx}:00",
                isSampleAiOutput = true
            )
        }

        return AiClipGenerationResult(
            clips = generated,
            isSampleOutput = true,
            noticeMessage = notice
        )
    }

    override suspend fun mineNichePlaybook(niche: String): AiNicheInsightResult {
        val cleanNiche = niche.ifBlank { "Tech" }
        val playbook = NichePlaybookEntity(
            niche = cleanNiche,
            hookFormula = "[Sample AI] \"I tested the top $cleanNiche workflow for 7 days so you don't have to\"",
            hookVelocityWps = 3.4,
            topTagsCsv = "#${cleanNiche.lowercase()}, #shorts, #breakdown, #tutorial, #workflow",
            dominantPalette = "#E63B2E / #111214 / #FACC15",
            recommendedCaptionStyle = SubtitleStyle.HORMOZI.name,
            avgRetentionBoostPct = 24,
            isSampleAiOutput = true
        )
        return AiNicheInsightResult(
            playbook = playbook,
            isSampleOutput = true,
            noticeMessage = notice
        )
    }

    override suspend fun regenerateMetadata(
        currentTitle: String,
        transcript: String
    ): AiMetadataResult {
        val topic = transcript.split(" ").take(5).joinToString(" ").ifBlank { currentTitle }
        return AiMetadataResult(
            clipTitle = "[Sample AI] The truth about $topic",
            stickerText = "DON'T SKIP",
            seoDescription = "[Sample AI Output] Re-framed hook around \"$topic\". Enable Firebase for live Gemini SEO generation.",
            hashtags = "#shorts #retention #hookstrategy #creators",
            isSampleOutput = true,
            noticeMessage = notice
        )
    }
}
