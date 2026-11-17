package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.CalendarContract
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Publish
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import com.example.data.local.GeneratedClipEntity
import com.example.data.media.SubtitleExporter
import com.example.model.FramingMode
import com.example.model.SubtitleStyle
import com.example.model.UploadStatus
import com.example.ui.components.StudioCard
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QueueScreen(
    clips: List<GeneratedClipEntity>,
    getShareableMp4Uri: (String) -> Uri?,
    exportSubtitleUri: suspend (GeneratedClipEntity, Boolean) -> Uri?,
    onSaveMp4ToGallery: (GeneratedClipEntity) -> Unit,
    onCopyMp4ToUri: (GeneratedClipEntity, Uri) -> Unit,
    onReExportMp4: (GeneratedClipEntity) -> Unit,
    onUpdateUploadState: (Long, UploadStatus, String) -> Unit,
    onDeleteClip: (Long) -> Unit,
    onOpenStudio: () -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var statusFilter by remember { mutableStateOf<UploadStatus?>(null) }

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

    val filtered = remember(clips, statusFilter) {
        val filter = statusFilter
        if (filter == null) clips
        else clips.filter { it.uploadStatus == filter.name }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("queue_screen"),
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
                    text = "Exports & Schedule",
                    style = MaterialTheme.typography.headlineMedium
                )
                Text(
                    text = "Save .MP4 clips to Movies/ClipForge, share to YouTube Shorts, export .SRT captions, or set upload reminders",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (clips.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = statusFilter == null,
                            onClick = { statusFilter = null },
                            label = { Text("All (${clips.size})") }
                        )
                        UploadStatus.entries.forEach { status ->
                            FilterChip(
                                selected = statusFilter == status,
                                onClick = {
                                    statusFilter = if (statusFilter == status) null else status
                                },
                                label = { Text(status.label) }
                            )
                        }
                    }
                }
            }
        }

        if (filtered.isEmpty()) {
            item {
                StudioCard(
                    modifier = Modifier
                        .widthIn(max = 840.dp)
                        .fillMaxWidth(),
                    accentBorder = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Publish,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                        Text(
                            text = "No exported clips yet",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = "Fetch a YouTube link or pick an MP4 in Clip Studio to render playable 9:16 MP4 clips and .SRT/.ASS files.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = onOpenStudio,
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Text("Open Clip Studio")
                        }
                    }
                }
            }
        } else {
            items(
                items = filtered,
                key = { it.id }
            ) { clip ->
                val currentStatus = UploadStatus.fromRaw(clip.uploadStatus)
                val framing = FramingMode.fromRaw(clip.framingMode)
                val caption = SubtitleStyle.fromRaw(clip.subtitleStyle)
                val duration = (clip.endSec - clip.startSec).coerceAtLeast(1)
                val mp4File = clip.exportedMp4Path?.let { File(it) }
                val hasRealMp4 = mp4File != null && mp4File.exists() && mp4File.length() > 0L

                StudioCard(
                    modifier = Modifier
                        .widthIn(max = 840.dp)
                        .fillMaxWidth(),
                    accentBorder = if (hasRealMp4) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.45f) else null
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Surface(
                                    shape = MaterialTheme.shapes.extraSmall,
                                    color = when (currentStatus) {
                                        UploadStatus.PUBLISHED, UploadStatus.EXPORTED -> MaterialTheme.colorScheme.tertiaryContainer
                                        UploadStatus.SCHEDULED -> MaterialTheme.colorScheme.secondaryContainer
                                        UploadStatus.DRAFT -> MaterialTheme.colorScheme.surfaceVariant
                                    }
                                ) {
                                    Text(
                                        text = currentStatus.label.uppercase(),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                                if (hasRealMp4) {
                                    Surface(
                                        shape = MaterialTheme.shapes.extraSmall,
                                        color = MaterialTheme.colorScheme.primaryContainer
                                    ) {
                                        Text(
                                            text = String.format(
                                                Locale.US,
                                                "MP4 %.1f MB",
                                                mp4File.length() / (1024.0 * 1024.0)
                                            ),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }

                            Text(
                                text = "Hook ${clip.viralityScore}/100 · ${duration}s",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Text(
                            text = clip.clipTitle,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        Text(
                            text = "${framing.label} · ${caption.label} · ${if (clip.loudnessNormalized) "-14 LUFS" else "Raw Audio"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (hasRealMp4) {
                                Button(
                                    onClick = {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                            onSaveMp4ToGallery(clip)
                                        } else {
                                            pendingClipForSafSave = clip
                                            val safeName = clip.clipTitle.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(32)
                                            saveDocumentLauncher.launch("${safeName.ifBlank { "clipforge_short" }}.mp4")
                                        }
                                    },
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Save MP4")
                                }

                                Button(
                                    onClick = {
                                        val uri = clip.exportedMp4Path?.let { getShareableMp4Uri(it) }
                                        if (uri == null) {
                                            onShowMessage("MP4 file not found in cache")
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
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Icon(Icons.Filled.VideoFile, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Upload / Share MP4")
                                }
                            } else {
                                Button(
                                    onClick = { onReExportMp4(clip) },
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Render MP4")
                                }
                            }

                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        val srtUri = exportSubtitleUri(clip, false)
                                        if (srtUri == null) {
                                            onShowMessage("Failed to create .SRT file")
                                        } else {
                                            val intent = Intent(Intent.ACTION_SEND).apply {
                                                type = "text/plain"
                                                putExtra(Intent.EXTRA_STREAM, srtUri)
                                                putExtra(Intent.EXTRA_SUBJECT, "${clip.clipTitle}.srt")
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            try {
                                                context.startActivity(Intent.createChooser(intent, "Export SRT File"))
                                            } catch (_: Exception) {
                                                onShowMessage("No app available to export .SRT files")
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(Icons.Filled.Subtitles, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Export .SRT")
                            }

                            OutlinedButton(
                                onClick = {
                                    val shareText = SubtitleExporter.buildSharePackage(clip)
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_SUBJECT, clip.clipTitle)
                                        putExtra(Intent.EXTRA_TEXT, shareText)
                                    }
                                    try {
                                        context.startActivity(Intent.createChooser(intent, "Share Metadata"))
                                    } catch (_: Exception) {
                                        onShowMessage("No app available to share text")
                                    }
                                },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Share Info")
                            }

                            OutlinedButton(
                                onClick = {
                                    val calIntent = Intent(Intent.ACTION_INSERT).apply {
                                        data = CalendarContract.Events.CONTENT_URI
                                        putExtra(CalendarContract.Events.TITLE, "Publish Short: ${clip.clipTitle}")
                                        putExtra(CalendarContract.Events.DESCRIPTION, clip.seoDescription)
                                    }
                                    try {
                                        context.startActivity(calIntent)
                                        onUpdateUploadState(clip.id, UploadStatus.SCHEDULED, "Calendar Reminder Set")
                                    } catch (_: Exception) {
                                        onShowMessage("No Calendar app found on this device")
                                    }
                                },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(Icons.Filled.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Calendar")
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = {
                                    onUpdateUploadState(clip.id, currentStatus.next(), clip.scheduledLabel)
                                },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Text("Mark: ${currentStatus.next().label}")
                            }

                            IconButton(
                                onClick = { onDeleteClip(clip.id) },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.DeleteOutline,
                                    contentDescription = "Delete ${clip.clipTitle}"
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
