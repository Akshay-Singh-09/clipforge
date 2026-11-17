package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.example.data.local.GeneratedClipEntity
import com.example.data.media.SubtitleExporter
import com.example.model.AsyncStatus
import com.example.model.FramingMode
import com.example.model.SubtitleStyle
import com.example.ui.StudioConfigState
import com.example.ui.components.FirebaseAiNoticeBanner
import com.example.ui.components.StudioCard
import com.example.ui.components.VerticalShortPreviewPlayer
import com.example.ui.components.ViralityRingGauge
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StudioScreen(
    studioState: StudioConfigState,
    clips: List<GeneratedClipEntity>,
    onUrlChange: (String) -> Unit,
    onFetchOEmbed: () -> Unit,
    onPickLocalVideo: (Uri) -> Unit,
    onClipTitleChange: (String) -> Unit,
    onCaptionScriptChange: (String) -> Unit,
    onStickerTextChange: (String) -> Unit,
    onTrimRangeChange: (Int, Int) -> Unit,
    onFramingModeChange: (FramingMode) -> Unit,
    onSubtitleStyleChange: (SubtitleStyle) -> Unit,
    onLoudnessChange: (Boolean) -> Unit,
    onSilenceTrimChange: (Boolean) -> Unit,
    onCreateAndTrimClip: () -> Unit,
    onSelectClip: (Long) -> Unit,
    onSaveClipEdits: (GeneratedClipEntity) -> Unit,
    onReExportMp4: (GeneratedClipEntity) -> Unit,
    onAttachLocalVideoToClip: (GeneratedClipEntity, Uri) -> Unit,
    onSaveMp4ToGallery: (GeneratedClipEntity) -> Unit,
    onCopyMp4ToUri: (GeneratedClipEntity, Uri) -> Unit,
    onRunSampleAiScore: (GeneratedClipEntity) -> Unit,
    getShareableMp4Uri: (String) -> Uri?,
    exportSubtitleUri: suspend (GeneratedClipEntity, Boolean) -> Uri?,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            onPickLocalVideo(uri)
        }
    }

    val isFetchingOEmbed = studioState.oembedStatus is AsyncStatus.Loading
    val isExporting = studioState.exportStatus is AsyncStatus.Loading

    val activeClip = remember(clips, studioState.selectedClipId) {
        clips.firstOrNull { it.id == studioState.selectedClipId } ?: clips.firstOrNull()
    }

    var pendingClipForAttach by remember { mutableStateOf<GeneratedClipEntity?>(null) }
    val attachVideoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        val target = pendingClipForAttach
        pendingClipForAttach = null
        if (uri != null && target != null) {
            onAttachLocalVideoToClip(target, uri)
        }
    }

    var pendingClipForSafSave by remember { mutableStateOf<GeneratedClipEntity?>(null) }
    val saveDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("video/mp4")
    ) { destUri ->
        val target = pendingClipForSafSave
        pendingClipForSafSave = null
        if (destUri != null && target != null) {
            onCopyMp4ToUri(target, destUri)
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("studio_screen"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Column(
                modifier = Modifier
                    .widthIn(max = 840.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Vertical Clip Studio",
                    style = MaterialTheme.typography.headlineMedium
                )
                Text(
                    text = "Fetch a YouTube link or pick a local MP4, trim & edit captions, preview playback, and export a real 9:16 MP4 video",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // 1. Source Picker Card
                StudioCard(
                    modifier = Modifier.fillMaxWidth(),
                    accentBorder = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "1. Video Source",
                            style = MaterialTheme.typography.titleMedium
                        )

                        OutlinedTextField(
                            value = studioState.sourceUrl,
                            onValueChange = onUrlChange,
                            label = { Text("YouTube Video URL") },
                            placeholder = { Text("https://www.youtube.com/watch?v=...") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("studio_url_input")
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = onFetchOEmbed,
                                enabled = !isFetchingOEmbed && studioState.sourceUrl.isNotBlank(),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("fetch_oembed_button")
                            ) {
                                if (isFetchingOEmbed) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Filled.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                                }
                                Spacer(Modifier.width(6.dp))
                                Text("Fetch Link")
                            }

                            Button(
                                onClick = {
                                    videoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                    )
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("pick_local_video_button")
                            ) {
                                Icon(Icons.Filled.VideoFile, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Pick MP4")
                            }
                        }

                        if (studioState.sourceTitle.isNotBlank() || studioState.localVideoMeta != null) {
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = studioState.sourceTitle.ifBlank { "Selected Local Video" },
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val meta = studioState.localVideoMeta
                                    if (meta != null) {
                                        Text(
                                            text = "${meta.width}x${meta.height} · ${meta.durationMs / 1000}s local MP4 ready for trimming",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                                        )
                                    } else if (studioState.sourceUrl.isNotBlank()) {
                                        Text(
                                            text = "YouTube link loaded. Cut & Export below to render a 9:16 captioned MP4, or tap Pick MP4 to trim raw footage.",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                                        )
                                        OutlinedButton(
                                            onClick = {
                                                try {
                                                    context.startActivity(
                                                        Intent(Intent.ACTION_VIEW, studioState.sourceUrl.toUri())
                                                    )
                                                } catch (_: Exception) {
                                                    onShowMessage("Could not open YouTube link")
                                                }
                                            },
                                            modifier = Modifier.minimumInteractiveComponentSize()
                                        ) {
                                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text("Watch Source")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 2. Trim, Framing & Caption Authoring Card
                StudioCard(
                    modifier = Modifier.fillMaxWidth(),
                    accentBorder = MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "2. Trim, 9:16 Framing & Captions",
                            style = MaterialTheme.typography.titleMedium
                        )

                        OutlinedTextField(
                            value = studioState.clipTitleInput,
                            onValueChange = onClipTitleChange,
                            label = { Text("Short Title") },
                            placeholder = { Text("Enter clip headline...") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("studio_clip_title_input")
                        )

                        OutlinedTextField(
                            value = studioState.stickerTextInput,
                            onValueChange = onStickerTextChange,
                            label = { Text("Sticker Badge") },
                            placeholder = { Text("KEY TAKEAWAY") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = studioState.captionScriptInput,
                            onValueChange = onCaptionScriptChange,
                            label = { Text("Karaoke Caption Script (.SRT / .ASS)") },
                            placeholder = { Text("Write or paste spoken lines to time captions...") },
                            minLines = 2,
                            modifier = Modifier.fillMaxWidth()
                        )

                        val maxBounds = studioState.maxDurationSec.coerceAtLeast(15).toFloat()
                        val safeStart = studioState.startSec.toFloat().coerceIn(0f, (maxBounds - 5f).coerceAtLeast(0f))
                        val minEnd = (safeStart + 5f).coerceAtMost(maxBounds)
                        val safeEnd = studioState.endSec.toFloat().coerceIn(minEnd, maxBounds)
                        val sliderRange = safeStart..safeEnd
                        val spanSec = (safeEnd - safeStart).toInt().coerceAtLeast(5)

                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Trim Range", style = MaterialTheme.typography.labelLarge)
                                Text(
                                    text = "${SubtitleExporter.formatMmSs(studioState.startSec)} – ${SubtitleExporter.formatMmSs(studioState.endSec)} (${spanSec}s)",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            RangeSlider(
                                value = sliderRange,
                                onValueChange = { newRange ->
                                    val start = newRange.start.toInt()
                                    val end = newRange.endInclusive.toInt().coerceAtLeast(start + 5)
                                    onTrimRangeChange(start, end)
                                },
                                valueRange = 0f..maxBounds
                            )
                        }

                        Text("9:16 Framing Layout", style = MaterialTheme.typography.labelLarge)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FramingMode.entries.forEach { mode ->
                                FilterChip(
                                    selected = studioState.framingMode == mode,
                                    onClick = { onFramingModeChange(mode) },
                                    label = { Text(mode.label) }
                                )
                            }
                        }

                        Text("Karaoke Caption Style", style = MaterialTheme.typography.labelLarge)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            SubtitleStyle.entries.forEach { style ->
                                FilterChip(
                                    selected = studioState.subtitleStyle == style,
                                    onClick = { onSubtitleStyleChange(style) },
                                    label = { Text(style.label) }
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Target -14 LUFS Profile", style = MaterialTheme.typography.bodyMedium)
                                Text("Metadata flag for upload checklist", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = studioState.loudnessNormalized, onCheckedChange = onLoudnessChange)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Tighten Pauses (>400ms)", style = MaterialTheme.typography.bodyMedium)
                                Text("Mark script cues for tight pacing", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = studioState.silenceTrimmed, onCheckedChange = onSilenceTrimChange)
                        }

                        Button(
                            onClick = onCreateAndTrimClip,
                            enabled = !isExporting,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("create_trim_clip_button")
                        ) {
                            if (isExporting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Icon(Icons.Filled.ContentCut, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(
                                when {
                                    isExporting -> "Rendering MP4..."
                                    studioState.localVideoMeta != null -> "Trim & Export MP4 Clip"
                                    else -> "Cut & Render 9:16 MP4"
                                }
                            )
                        }
                    }
                }
            }
        }

        if (clips.isNotEmpty() && activeClip != null) {
            item {
                Column(
                    modifier = Modifier
                        .widthIn(max = 840.dp)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "3. Live 9:16 Video Preview & MP4 Export",
                        style = MaterialTheme.typography.titleLarge
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        clips.take(8).forEachIndexed { idx, clip ->
                            FilterChip(
                                selected = clip.id == activeClip.id,
                                onClick = { onSelectClip(clip.id) },
                                label = { Text("Clip ${idx + 1} · ${clip.viralityScore}") }
                            )
                        }
                    }

                    StudioClipEditorCard(
                        clip = activeClip,
                        isRendering = isExporting,
                        previewFrames = studioState.localPreviewFrames,
                        onSave = onSaveClipEdits,
                        onReExportMp4 = onReExportMp4,
                        onAttachLocalMp4 = { target ->
                            pendingClipForAttach = target
                            attachVideoLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                            )
                        },
                        onSaveMp4ToDevice = { target ->
                            val mp4File = target.exportedMp4Path?.let { File(it) }
                            if (mp4File == null || !mp4File.exists()) {
                                onReExportMp4(target)
                            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                onSaveMp4ToGallery(target)
                            } else {
                                pendingClipForSafSave = target
                                val safeName = target.clipTitle.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(32)
                                saveDocumentLauncher.launch("${safeName.ifBlank { "clipforge_short" }}.mp4")
                            }
                        },
                        onRunSampleAiScore = onRunSampleAiScore,
                        onShareMp4 = { clip ->
                            val uri = clip.exportedMp4Path?.let { getShareableMp4Uri(it) }
                            if (uri == null) {
                                onShowMessage("Rendering MP4 first — tap Render MP4 below")
                            } else {
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "video/mp4"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    putExtra(Intent.EXTRA_SUBJECT, clip.clipTitle)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                try {
                                    context.startActivity(Intent.createChooser(intent, "Upload / Share MP4"))
                                } catch (_: Exception) {
                                    onShowMessage("No app available to share video files")
                                }
                            }
                        },
                        onExportSubtitle = { clip, isAss ->
                            scope.launch {
                                val uri = exportSubtitleUri(clip, isAss)
                                if (uri == null) {
                                    onShowMessage("Failed to create subtitle file")
                                } else {
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        putExtra(Intent.EXTRA_SUBJECT, clip.clipTitle)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    try {
                                        context.startActivity(
                                            Intent.createChooser(intent, if (isAss) "Export .ASS Captions" else "Export .SRT Captions")
                                        )
                                    } catch (_: Exception) {
                                        onShowMessage("No app available to export subtitle files")
                                    }
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StudioClipEditorCard(
    clip: GeneratedClipEntity,
    isRendering: Boolean,
    previewFrames: List<android.graphics.Bitmap>,
    onSave: (GeneratedClipEntity) -> Unit,
    onReExportMp4: (GeneratedClipEntity) -> Unit,
    onAttachLocalMp4: (GeneratedClipEntity) -> Unit,
    onSaveMp4ToDevice: (GeneratedClipEntity) -> Unit,
    onRunSampleAiScore: (GeneratedClipEntity) -> Unit,
    onShareMp4: (GeneratedClipEntity) -> Unit,
    onExportSubtitle: (GeneratedClipEntity, Boolean) -> Unit
) {
    var titleText by remember(clip.id, clip.clipTitle) { mutableStateOf(clip.clipTitle) }
    var stickerText by remember(clip.id, clip.stickerText) { mutableStateOf(clip.stickerText) }
    var transcriptText by remember(clip.id, clip.transcriptSnippet) { mutableStateOf(clip.transcriptSnippet) }
    var seoText by remember(clip.id, clip.seoDescription) { mutableStateOf(clip.seoDescription) }
    var framingMode by remember(clip.id, clip.framingMode) {
        mutableStateOf(FramingMode.fromRaw(clip.framingMode))
    }
    var subtitleStyle by remember(clip.id, clip.subtitleStyle) {
        mutableStateOf(SubtitleStyle.fromRaw(clip.subtitleStyle))
    }
    val maxClipBound = maxOf(180f, clip.endSec.toFloat())
    val initStart = clip.startSec.toFloat().coerceIn(0f, (maxClipBound - 5f).coerceAtLeast(0f))
    val initEnd = clip.endSec.toFloat().coerceIn((initStart + 5f).coerceAtMost(maxClipBound), maxClipBound)
    var trimRange by remember(clip.id, clip.startSec, clip.endSec) {
        mutableStateOf(initStart..initEnd)
    }

    val durationSec = (trimRange.endInclusive - trimRange.start).toInt().coerceAtLeast(5)
    val mp4File = clip.exportedMp4Path?.let { File(it) }
    val hasRealMp4 = mp4File != null && mp4File.exists() && mp4File.length() > 0L
    val hasPendingRenderEdits = titleText.trim() != clip.clipTitle ||
        stickerText.trim() != clip.stickerText ||
        transcriptText.trim() != clip.transcriptSnippet ||
        framingMode.name != clip.framingMode ||
        subtitleStyle.name != clip.subtitleStyle ||
        trimRange.start.toInt() != clip.startSec ||
        trimRange.endInclusive.toInt() != clip.endSec

    StudioCard(
        modifier = Modifier.fillMaxWidth(),
        accentBorder = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (clip.isSampleAiOutput) {
                FirebaseAiNoticeBanner()
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = clip.sourceTitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Hook ${clip.hookStrength} · Completeness ${clip.completeness} · Payoff ${clip.emotionalPayoff}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Surface(
                        shape = MaterialTheme.shapes.extraSmall,
                        color = when {
                            hasRealMp4 && !hasPendingRenderEdits -> MaterialTheme.colorScheme.tertiaryContainer
                            else -> MaterialTheme.colorScheme.secondaryContainer
                        }
                    ) {
                        val sizeText = when {
                            !hasRealMp4 -> "TAP RENDER MP4 TO EXPORT VIDEO"
                            hasPendingRenderEdits -> "EDITS PENDING · TAP UPDATE MP4"
                            else -> String.format(Locale.US, "MP4 READY · %.1f MB", mp4File.length() / (1024.0 * 1024.0))
                        }
                        Text(
                            text = sizeText,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                hasRealMp4 && !hasPendingRenderEdits -> MaterialTheme.colorScheme.onTertiaryContainer
                                else -> MaterialTheme.colorScheme.onSecondaryContainer
                            },
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
                ViralityRingGauge(score = clip.viralityScore)
            }

            VerticalShortPreviewPlayer(
                clipTitle = titleText,
                transcript = transcriptText,
                stickerText = stickerText,
                durationSec = durationSec,
                framingMode = framingMode,
                subtitleStyle = subtitleStyle,
                thumbnailUrl = clip.thumbnailUrl,
                localVideoUri = clip.localVideoUri,
                exportedMp4Path = clip.exportedMp4Path,
                previewFrames = previewFrames
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Clip Trim Window", style = MaterialTheme.typography.labelLarge)
                    Text(
                        text = "${SubtitleExporter.formatMmSs(trimRange.start.toInt())} – ${SubtitleExporter.formatMmSs(trimRange.endInclusive.toInt())} (${durationSec}s)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                RangeSlider(
                    value = trimRange,
                    onValueChange = { next ->
                        if (next.endInclusive - next.start >= 5f) {
                            trimRange = next
                        }
                    },
                    valueRange = 0f..maxClipBound
                )
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FramingMode.entries.forEach { mode ->
                    FilterChip(
                        selected = framingMode == mode,
                        onClick = { framingMode = mode },
                        label = { Text(mode.label) }
                    )
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SubtitleStyle.entries.forEach { style ->
                    FilterChip(
                        selected = subtitleStyle == style,
                        onClick = { subtitleStyle = style },
                        label = { Text(style.label) }
                    )
                }
            }

            OutlinedTextField(
                value = titleText,
                onValueChange = { titleText = it },
                label = { Text("Short Title") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = stickerText,
                onValueChange = { stickerText = it },
                label = { Text("Sticker Text") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = transcriptText,
                onValueChange = { transcriptText = it },
                label = { Text("Caption Script") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = seoText,
                onValueChange = { seoText = it },
                label = { Text("Upload Description") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth()
            )

            val buildUpdatedClip = {
                val safeStart = trimRange.start.toInt().coerceAtLeast(0)
                val safeEnd = trimRange.endInclusive.toInt().coerceAtLeast(safeStart + 5)
                clip.copy(
                    clipTitle = titleText.trim().ifBlank { clip.clipTitle },
                    stickerText = stickerText.trim(),
                    transcriptSnippet = transcriptText.trim().ifBlank { clip.transcriptSnippet },
                    seoDescription = seoText.trim(),
                    framingMode = framingMode.name,
                    subtitleStyle = subtitleStyle.name,
                    startSec = safeStart,
                    endSec = safeEnd
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { onSave(buildUpdatedClip()) },
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                ) {
                    Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Save Edits")
                }

                Button(
                    onClick = { onReExportMp4(buildUpdatedClip()) },
                    enabled = !isRendering,
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("re_export_mp4_button")
                ) {
                    if (isRendering) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(if (hasRealMp4) "Update MP4" else "Render MP4")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { onAttachLocalMp4(buildUpdatedClip()) },
                    enabled = !isRendering,
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                ) {
                    Icon(Icons.Filled.VideoFile, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Attach MP4")
                }

                OutlinedButton(
                    onClick = { onRunSampleAiScore(buildUpdatedClip()) },
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                ) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Sample AI")
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { onSaveMp4ToDevice(buildUpdatedClip()) },
                    enabled = !isRendering,
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("save_mp4_device_button")
                ) {
                    Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Save MP4")
                }

                Button(
                    onClick = {
                        val updated = buildUpdatedClip()
                        if (hasRealMp4) {
                            onShareMp4(updated)
                        } else {
                            onReExportMp4(updated)
                        }
                    },
                    enabled = !isRendering,
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("share_upload_mp4_button")
                ) {
                    Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (hasRealMp4) "Upload / Share MP4" else "Render MP4")
                }

                OutlinedButton(
                    onClick = { onExportSubtitle(buildUpdatedClip(), false) },
                    modifier = Modifier.height(48.dp)
                ) {
                    Icon(Icons.Filled.Subtitles, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Export .SRT")
                }

                OutlinedButton(
                    onClick = { onExportSubtitle(buildUpdatedClip(), true) },
                    modifier = Modifier.height(48.dp)
                ) {
                    Icon(Icons.Filled.Subtitles, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Export .ASS")
                }
            }
        }
    }
}
