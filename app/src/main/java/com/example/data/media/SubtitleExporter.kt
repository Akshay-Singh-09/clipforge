package com.example.data.media

import com.example.data.local.GeneratedClipEntity
import java.util.Locale

object SubtitleExporter {

    fun generateSrt(transcript: String, durationSec: Int): String {
        val words = transcript.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return ""
        val safeDuration = durationSec.coerceAtLeast(3)
        val chunkSize = 4
        val chunks = words.chunked(chunkSize)
        val msPerChunk = (safeDuration * 1000L) / chunks.size.coerceAtLeast(1)

        return buildString {
            chunks.forEachIndexed { index, group ->
                val startMs = index * msPerChunk
                val endMs = ((index + 1) * msPerChunk).coerceAtMost(safeDuration * 1000L)
                appendLine(index + 1)
                appendLine("${formatSrtTime(startMs)} --> ${formatSrtTime(endMs)}")
                appendLine(group.joinToString(" "))
                appendLine()
            }
        }.trim()
    }

    fun generateAssKaraoke(transcript: String, durationSec: Int, styleName: String): String {
        val words = transcript.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val safeDuration = durationSec.coerceAtLeast(3)
        val centisPerWord = ((safeDuration * 100) / words.size.coerceAtLeast(1)).coerceIn(15, 90)
        val karaokeBody = words.joinToString(" ") { word -> "{\\k$centisPerWord}$word" }

        return buildString {
            appendLine("[Script Info]")
            appendLine("Title: ClipForge Karaoke Export ($styleName)")
            appendLine("ScriptType: v4.00+")
            appendLine("PlayResX: 1080")
            appendLine("PlayResY: 1920")
            appendLine()
            appendLine("[V4+ Styles]")
            appendLine("Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Alignment, MarginV")
            appendLine("Style: Default,Plus Jakarta Sans,78,&H00FFFFFF,&H0000D7FF,&H00000000,&H80000000,-1,2,260")
            appendLine()
            appendLine("[Events]")
            appendLine("Format: Layer, Start, End, Style, Text")
            if (karaokeBody.isNotEmpty()) {
                appendLine("Dialogue: 0,0:00:00.00,${formatAssTime(safeDuration * 1000L)},Default,$karaokeBody")
            }
        }.trim()
    }

    fun buildSharePackage(clip: GeneratedClipEntity): String {
        val duration = (clip.endSec - clip.startSec).coerceAtLeast(1)
        val srt = generateSrt(clip.transcriptSnippet, duration)
        return buildString {
            appendLine("CLIPFORGE SHORT PACKAGE")
            appendLine("Title: ${clip.clipTitle}")
            appendLine("Virality Score: ${clip.viralityScore}/100 (Hook ${clip.hookStrength}, Payoff ${clip.emotionalPayoff})")
            appendLine("Framing: ${clip.framingMode} · Captions: ${clip.subtitleStyle}")
            appendLine("Audio: ${if (clip.loudnessNormalized) "-14 LUFS" else "Original"} · Silence Trim: ${if (clip.silenceTrimmed) "400ms+" else "Off"}")
            appendLine("Window: ${formatMmSs(clip.startSec)} - ${formatMmSs(clip.endSec)} (${duration}s)")
            if (clip.sourceUrl.isNotBlank()) {
                appendLine("Source: ${clip.sourceUrl}")
            }
            appendLine()
            appendLine("Description:")
            appendLine(clip.seoDescription)
            appendLine(clip.hashtags)
            appendLine()
            appendLine("--- SRT CAPTIONS ---")
            appendLine(srt)
        }.trim()
    }

    fun formatMmSs(totalSec: Int): String {
        val clamped = totalSec.coerceAtLeast(0)
        val min = clamped / 60
        val sec = clamped % 60
        return String.format(Locale.US, "%02d:%02d", min, sec)
    }

    private fun formatSrtTime(ms: Long): String {
        val totalSeconds = ms / 1000
        val millis = ms % 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.US, "%02d:%02d:%02d,%03d", hours, minutes, seconds, millis)
    }

    private fun formatAssTime(ms: Long): String {
        val totalSeconds = ms / 1000
        val centis = (ms % 1000) / 10
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.US, "%d:%02d:%02d.%02d", hours, minutes, seconds, centis)
    }
}
