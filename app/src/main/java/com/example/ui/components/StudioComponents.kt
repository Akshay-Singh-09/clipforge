package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.view.Surface as NativeSurface
import android.view.TextureView
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import coil.compose.AsyncImage
import com.example.R
import com.example.data.media.SubtitleExporter
import com.example.model.ChannelHealthStatus
import com.example.model.FramingMode
import com.example.model.SubtitleStyle
import com.example.model.ThemeMode
import com.example.ui.theme.AmberAccentDark
import com.example.ui.theme.AmberAccentLight
import com.example.ui.theme.EmeraldTertiaryDark
import com.example.ui.theme.EmeraldTertiaryLight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun StudioCard(
    modifier: Modifier = Modifier,
    accentBorder: Color? = null,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = 1.dp,
            color = accentBorder ?: MaterialTheme.colorScheme.outlineVariant
        ),
        tonalElevation = 1.dp
    ) {
        content()
    }
}

@Composable
fun HealthBadge(
    status: ChannelHealthStatus,
    modifier: Modifier = Modifier
) {
    val isDark = MaterialTheme.colorScheme.background.red < 0.2f
    val (dotColor, bgColor, textColor) = when (status) {
        ChannelHealthStatus.DOING_GREAT -> Triple(
            if (isDark) EmeraldTertiaryDark else EmeraldTertiaryLight,
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer
        )
        ChannelHealthStatus.STABLE -> Triple(
            if (isDark) AmberAccentDark else AmberAccentLight,
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer
        )
        ChannelHealthStatus.NEEDS_ATTENTION -> Triple(
            MaterialTheme.colorScheme.error,
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer
        )
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = bgColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Text(
                text = status.label,
                style = MaterialTheme.typography.labelSmall,
                color = textColor,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun FirebaseAiNoticeBanner(
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("ai_firebase_notice_banner"),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.78f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = stringResource(R.string.ai_firebase_notice),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
fun VelocitySparklineChart(
    values: List<Float>,
    lineColor: Color,
    modifier: Modifier = Modifier
) {
    if (values.size < 2) return
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val pad = 4.dp.toPx()
        val usableW = (w - pad * 2).coerceAtLeast(1f)
        val usableH = (h - pad * 2).coerceAtLeast(1f)
        val minVal = values.minOrNull() ?: 0f
        val maxVal = (values.maxOrNull() ?: 1f).coerceAtLeast(minVal + 0.01f)

        val points = values.mapIndexed { idx, v ->
            val x = pad + (idx.toFloat() / (values.size - 1)) * usableW
            val norm = ((v - minVal) / (maxVal - minVal)).coerceIn(0f, 1f)
            val y = pad + (1f - norm) * usableH
            Offset(x, y)
        }

        val strokePath = Path().apply {
            moveTo(points.first().x, points.first().y)
            for (i in 1 until points.size) {
                val prev = points[i - 1]
                val curr = points[i]
                val midX = (prev.x + curr.x) / 2f
                cubicTo(midX, prev.y, midX, curr.y, curr.x, curr.y)
            }
        }

        val fillPath = Path().apply {
            addPath(strokePath)
            lineTo(points.last().x, h)
            lineTo(points.first().x, h)
            close()
        }

        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(lineColor.copy(alpha = 0.28f), Color.Transparent)
            )
        )

        drawPath(
            path = strokePath,
            color = lineColor,
            style = Stroke(
                width = 2.4.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )

        val last = points.last()
        drawCircle(color = lineColor, radius = 3.8.dp.toPx(), center = last)
    }
}

@Composable
fun ViralityRingGauge(
    score: Int,
    modifier: Modifier = Modifier
) {
    val clamped = score.coerceIn(0, 100)
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val activeColor = when {
        clamped >= 85 -> MaterialTheme.colorScheme.tertiary
        clamped >= 70 -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.secondary
    }

    Box(
        modifier = modifier.size(56.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokePx = 4.5.dp.toPx()
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )
            drawArc(
                color = activeColor,
                startAngle = -90f,
                sweepAngle = 360f * (clamped / 100f),
                useCenter = false,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "$clamped",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "HOOK",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun VerticalShortPreviewPlayer(
    clipTitle: String,
    transcript: String,
    stickerText: String,
    durationSec: Int,
    framingMode: FramingMode,
    subtitleStyle: SubtitleStyle,
    thumbnailUrl: String?,
    localVideoUri: String?,
    exportedMp4Path: String?,
    previewFrames: List<Bitmap>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    var progress by remember(clipTitle, durationSec, exportedMp4Path) { mutableFloatStateOf(0f) }
    var activePlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    val playableUri: Uri? = remember(exportedMp4Path, localVideoUri) {
        when {
            !exportedMp4Path.isNullOrBlank() && File(exportedMp4Path).exists() ->
                Uri.fromFile(File(exportedMp4Path))
            !localVideoUri.isNullOrBlank() ->
                localVideoUri.toUri()
            else -> null
        }
    }

    // Extract a fallback preview frame from exported MP4 or local video when previewFrames is empty
    val extractedFallbackFrame by produceState<Bitmap?>(initialValue = null, playableUri, previewFrames) {
        value = if (previewFrames.isNotEmpty() || playableUri == null) {
            null
        } else {
            withContext(Dispatchers.IO) {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(context, playableUri)
                    retriever.getFrameAtTime(500_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                } catch (_: Exception) {
                    null
                } finally {
                    try {
                        retriever.release()
                    } catch (_: Exception) {
                    }
                }
            }
        }
    }

    val words = remember(transcript) {
        transcript.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    }

    LaunchedEffect(isPlaying, durationSec, playableUri) {
        val safeSec = durationSec.coerceAtLeast(5)
        while (isPlaying) {
            delay(80L)
            val mp = activePlayer
            val mpDur = try {
                mp?.duration ?: 0
            } catch (_: Exception) {
                0
            }
            if (playableUri != null && mp != null && mpDur > 0) {
                val pos = try {
                    mp.currentPosition
                } catch (_: Exception) {
                    0
                }
                progress = (pos.toFloat() / mpDur.toFloat()).coerceIn(0f, 1f)
            } else {
                progress += 0.08f / safeSec.toFloat()
                if (progress >= 1f) {
                    progress = 0f
                }
            }
        }
    }

    val activeWordIndex = if (words.isEmpty()) -1
    else (progress * words.size).toInt().coerceIn(0, words.lastIndex)

    val visibleWindow = remember(words, activeWordIndex) {
        if (words.isEmpty()) emptyList()
        else {
            val start = (activeWordIndex - 2).coerceAtLeast(0)
            val end = (start + 5).coerceAtMost(words.size)
            words.subList(start, end).mapIndexed { offset, w ->
                (start + offset == activeWordIndex) to w
            }
        }
    }

    val frameBitmap = remember(previewFrames, extractedFallbackFrame, progress) {
        when {
            previewFrames.isNotEmpty() -> {
                val idx = (progress * previewFrames.size).toInt().coerceIn(0, previewFrames.lastIndex)
                previewFrames[idx]
            }
            extractedFallbackFrame != null -> extractedFallbackFrame
            else -> null
        }
    }

    val highlightColor by animateColorAsState(
        targetValue = Color(subtitleStyle.accentHex),
        label = "captionHighlight"
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 260.dp)
                .fillMaxWidth()
                .aspectRatio(9f / 16f)
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFF0A0F1A))
                .clickable { isPlaying = !isPlaying }
                .border(
                    width = 2.dp,
                    brush = Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.secondary,
                            MaterialTheme.colorScheme.tertiary
                        )
                    ),
                    shape = RoundedCornerShape(18.dp)
                )
        ) {
            when {
                playableUri != null && isPlaying -> {
                    key(playableUri, framingMode) {
                        var playerInstance by remember { mutableStateOf<MediaPlayer?>(null) }
                        var surfaceInstance by remember { mutableStateOf<NativeSurface?>(null) }

                        DisposableEffect(playableUri) {
                            onDispose {
                                activePlayer = null
                                try {
                                    playerInstance?.stop()
                                } catch (_: Exception) {
                                }
                                try {
                                    playerInstance?.release()
                                } catch (_: Exception) {
                                }
                                try {
                                    surfaceInstance?.release()
                                } catch (_: Exception) {
                                }
                                playerInstance = null
                                surfaceInstance = null
                            }
                        }

                        AndroidView(
                            factory = { ctx ->
                                TextureView(ctx).apply {
                                    surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                                        override fun onSurfaceTextureAvailable(
                                            st: SurfaceTexture,
                                            viewW: Int,
                                            viewH: Int
                                        ) {
                                            val surface = NativeSurface(st)
                                            surfaceInstance = surface
                                            val mp = MediaPlayer()
                                            playerInstance = mp
                                            activePlayer = mp
                                            try {
                                                mp.setDataSource(ctx, playableUri)
                                                mp.setSurface(surface)
                                                mp.isLooping = true
                                                mp.setOnVideoSizeChangedListener { _, videoW, videoH ->
                                                    if (videoW > 0 && videoH > 0 && viewW > 0 && viewH > 0) {
                                                        setTransform(
                                                            computeVideoTransformMatrix(
                                                                viewW = viewW,
                                                                viewH = viewH,
                                                                videoW = videoW,
                                                                videoH = videoH,
                                                                centerCrop = framingMode != FramingMode.BLURRED_LETTERBOX
                                                            )
                                                        )
                                                    }
                                                }
                                                mp.setOnPreparedListener { prepared ->
                                                    val dur = prepared.duration
                                                    if (dur > 0 && progress > 0f) {
                                                        prepared.seekTo((progress * dur).toInt())
                                                    }
                                                    prepared.start()
                                                }
                                                mp.setOnErrorListener { _, _, _ -> true }
                                                mp.prepareAsync()
                                            } catch (_: Exception) {
                                                activePlayer = null
                                            }
                                        }

                                        override fun onSurfaceTextureSizeChanged(
                                            st: SurfaceTexture,
                                            w: Int,
                                            h: Int
                                        ) {
                                            val mp = playerInstance ?: return
                                            val vw = mp.videoWidth
                                            val vh = mp.videoHeight
                                            if (vw > 0 && vh > 0 && w > 0 && h > 0) {
                                                setTransform(
                                                    computeVideoTransformMatrix(
                                                        viewW = w,
                                                        viewH = h,
                                                        videoW = vw,
                                                        videoH = vh,
                                                        centerCrop = framingMode != FramingMode.BLURRED_LETTERBOX
                                                    )
                                                )
                                            }
                                        }

                                        override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                                            activePlayer = null
                                            try {
                                                playerInstance?.stop()
                                            } catch (_: Exception) {
                                            }
                                            try {
                                                playerInstance?.release()
                                            } catch (_: Exception) {
                                            }
                                            try {
                                                surfaceInstance?.release()
                                            } catch (_: Exception) {
                                            }
                                            playerInstance = null
                                            surfaceInstance = null
                                            return true
                                        }

                                        override fun onSurfaceTextureUpdated(st: SurfaceTexture) = Unit
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                frameBitmap != null -> {
                    Image(
                        bitmap = frameBitmap.asImageBitmap(),
                        contentDescription = "Preview frame for $clipTitle",
                        contentScale = if (framingMode == FramingMode.BLURRED_LETTERBOX) ContentScale.Fit else ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                !thumbnailUrl.isNullOrBlank() -> {
                    AsyncImage(
                        model = thumbnailUrl,
                        contentDescription = "Source thumbnail for $clipTitle",
                        contentScale = if (framingMode == FramingMode.BLURRED_LETTERBOX) ContentScale.Fit else ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                else -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        Color(0xFF1E1B4B),
                                        Color(0xFF0F172A),
                                        Color(0xFF1F1224)
                                    )
                                )
                            )
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.4f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.78f)
                            )
                        )
                    )
            )

            // Framing guide overlay
            when (framingMode) {
                FramingMode.SMART_CENTER -> {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxWidth(0.76f)
                            .aspectRatio(1f)
                            .border(
                                1.dp,
                                Color.White.copy(alpha = 0.4f),
                                RoundedCornerShape(12.dp)
                            )
                    )
                }
                FramingMode.SPLIT_SCREEN -> {
                    HorizontalDivider(
                        modifier = Modifier.align(Alignment.Center),
                        thickness = 1.5.dp,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
                FramingMode.BLURRED_LETTERBOX -> {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .border(1.dp, Color.White.copy(alpha = 0.45f))
                    )
                }
            }

            // Center play badge when paused
            if (!isPlaying) {
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.6f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.45f)),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(54.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = "Play video preview",
                            tint = Color.White,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }
            }

            // Top badge row inside 9:16 frame
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color.Black.copy(alpha = 0.65f)
                ) {
                    Text(
                        text = if (playableUri != null) "MP4 · ${framingMode.label.uppercase()}" else framingMode.label.uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                    )
                }

                if (stickerText.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = highlightColor
                    ) {
                        Text(
                            text = stickerText.uppercase(),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 10.sp
                            ),
                            color = Color(0xFF0F172A),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // Live karaoke captions
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (visibleWindow.isNotEmpty()) {
                    val captionText = buildAnnotatedString {
                        visibleWindow.forEachIndexed { idx, (isActive, word) ->
                            withStyle(
                                SpanStyle(
                                    color = if (isActive) highlightColor else Color.White,
                                    fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Bold,
                                    fontSize = if (isActive) 18.sp else 16.sp
                                )
                            ) {
                                append(
                                    if (subtitleStyle == SubtitleStyle.HORMOZI || subtitleStyle == SubtitleStyle.BOLD) {
                                        word.uppercase()
                                    } else {
                                        word
                                    }
                                )
                            }
                            if (idx < visibleWindow.lastIndex) append(" ")
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color.Black.copy(alpha = 0.72f)
                    ) {
                        Text(
                            text = captionText,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { isPlaying = !isPlaying },
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("preview_play_pause_button")
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Pause preview" else "Play preview",
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Slider(
                value = progress,
                onValueChange = { newProgress ->
                    progress = newProgress
                    val mp = activePlayer
                    val dur = try {
                        mp?.duration ?: 0
                    } catch (_: Exception) {
                        0
                    }
                    if (mp != null && dur > 0) {
                        try {
                            mp.seekTo((newProgress * dur).toInt())
                        } catch (_: Exception) {
                        }
                    }
                },
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary
                )
            )

            Spacer(Modifier.width(8.dp))

            val currentSec = (progress * durationSec).toInt()
            Text(
                text = "${SubtitleExporter.formatMmSs(currentSec)} / ${SubtitleExporter.formatMmSs(durationSec)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun computeVideoTransformMatrix(
    viewW: Int,
    viewH: Int,
    videoW: Int,
    videoH: Int,
    centerCrop: Boolean
): Matrix {
    val sx = viewW.toFloat() / videoW.toFloat()
    val sy = viewH.toFloat() / videoH.toFloat()
    val scale = if (centerCrop) maxOf(sx, sy) else minOf(sx, sy)
    val scaledW = videoW * scale
    val scaledH = videoH * scale
    val matrix = Matrix()
    matrix.setScale(scaledW / viewW.toFloat(), scaledH / viewH.toFloat(), viewW / 2f, viewH / 2f)
    return matrix
}

@Composable
fun AboutDialog(
    currentThemeMode: ThemeMode,
    onSelectThemeMode: (ThemeMode) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Outlined.Tune,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = stringResource(R.string.about_title),
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.about_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.about_made_by),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.testTag("about_made_by_google_text")
                        )
                        Text(
                            text = "Version 1.0 · Live YouTube RSS, On-Device MP4 Trimmer & Room Storage",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                        )
                    }
                }

                Text(
                    text = stringResource(R.string.about_ai_status),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Text(
                    text = "Appearance",
                    style = MaterialTheme.typography.titleSmall
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = currentThemeMode == mode,
                            onClick = { onSelectThemeMode(mode) },
                            label = { Text(mode.label, style = MaterialTheme.typography.labelLarge) },
                            modifier = Modifier.testTag("theme_mode_${mode.name.lowercase()}")
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("about_close_button")
            ) {
                Text("Done")
            }
        }
    )
}
