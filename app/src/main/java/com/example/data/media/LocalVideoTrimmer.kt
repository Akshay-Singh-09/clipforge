package com.example.data.media

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.view.Surface
import androidx.core.content.FileProvider
import com.example.model.FramingMode
import com.example.model.SubtitleStyle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit
import kotlin.math.max

class LocalVideoTrimmer(
    private val context: Context,
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
) {

    suspend fun trimToMp4(
        sourceUri: Uri,
        startSec: Int,
        endSec: Int
    ): Result<File> = withContext(Dispatchers.IO) {
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        var outputFile: File? = null
        var muxerStarted = false
        var wroteSample = false
        try {
            extractor.setDataSource(context, sourceUri, null)

            val safeStartUs = startSec.coerceAtLeast(0) * 1_000_000L
            val safeEndUs = endSec.coerceAtLeast(startSec + 1) * 1_000_000L

            val outputDir = File(context.cacheDir, "clips").apply { mkdirs() }
            outputFile = File(outputDir, "clipforge_${System.currentTimeMillis()}.mp4")

            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            val trackIndexMap = mutableMapOf<Int, Int>()
            var maxBufferSize = 1024 * 1024

            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("video/") || mime.startsWith("audio/")) {
                    extractor.selectTrack(i)
                    val dstIndex = muxer.addTrack(format)
                    trackIndexMap[i] = dstIndex
                    if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                        maxBufferSize = maxOf(maxBufferSize, format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE))
                    }
                }
            }

            if (trackIndexMap.isEmpty()) {
                outputFile.delete()
                return@withContext Result.failure(IllegalStateException("No supported audio/video tracks found"))
            }

            val rotation = readRotationDegrees(sourceUri)
            if (rotation != 0) {
                muxer.setOrientationHint(rotation)
            }

            muxer.start()
            muxerStarted = true
            extractor.seekTo(safeStartUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

            var buffer = ByteBuffer.allocate(maxBufferSize)
            val bufferInfo = MediaCodec.BufferInfo()
            var basePtsUs: Long? = null

            while (true) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val sampleSize = extractor.sampleSize
                    if (sampleSize > buffer.capacity()) {
                        buffer = ByteBuffer.allocate(sampleSize.toInt())
                    }
                }
                bufferInfo.offset = 0
                bufferInfo.size = extractor.readSampleData(buffer, 0)
                if (bufferInfo.size < 0) {
                    bufferInfo.size = 0
                    break
                }

                val sampleTimeUs = extractor.sampleTime
                if (sampleTimeUs < 0L || sampleTimeUs > safeEndUs) {
                    break
                }

                if (basePtsUs == null) {
                    basePtsUs = sampleTimeUs
                }
                bufferInfo.presentationTimeUs = (sampleTimeUs - basePtsUs).coerceAtLeast(0L)
                bufferInfo.flags = mapSampleFlagsToCodecFlags(extractor.sampleFlags)

                val srcTrack = extractor.sampleTrackIndex
                val dstTrack = trackIndexMap[srcTrack]
                if (dstTrack != null) {
                    muxer.writeSampleData(dstTrack, buffer, bufferInfo)
                    wroteSample = true
                }
                extractor.advance()
            }

            if (!wroteSample) {
                outputFile.delete()
                return@withContext Result.failure(IllegalStateException("No frames found in selected trim range"))
            }

            muxer.stop()
            muxerStarted = false

            if (outputFile.length() == 0L) {
                outputFile.delete()
                return@withContext Result.failure(IllegalStateException("Trim produced an empty clip"))
            }
            Result.success(outputFile)
        } catch (e: CancellationException) {
            outputFile?.delete()
            throw e
        } catch (e: Exception) {
            outputFile?.delete()
            Result.failure(e)
        } finally {
            if (muxerStarted) {
                try {
                    muxer?.stop()
                } catch (_: Exception) {
                }
            }
            try {
                muxer?.release()
            } catch (_: Exception) {
            }
            try {
                extractor.release()
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Encodes a genuine 720x1280 (9:16) H.264 MP4 highlight video with smooth zoom motion,
     * framing layout, sticker badge, and frame-synchronized karaoke captions.
     * Used when creating/exporting clips from YouTube link metadata or caption scripts.
     */
    suspend fun renderVerticalHighlightMp4(
        title: String,
        transcript: String,
        stickerText: String,
        framingMode: FramingMode,
        subtitleStyle: SubtitleStyle,
        durationSec: Int,
        thumbnailUrl: String?
    ): Result<File> = withContext(Dispatchers.IO) {
        val width = 720
        val height = 1280
        val fps = 15
        val clampedDurationSec = durationSec.coerceIn(5, 30)
        val totalFrames = clampedDurationSec * fps
        val frameDurationUs = 1_000_000L / fps

        val outputDir = File(context.cacheDir, "clips").apply { mkdirs() }
        val outputFile = File(outputDir, "clipforge_short_${System.currentTimeMillis()}.mp4")
        val thumbBitmap = downloadThumbnailBitmap(thumbnailUrl)

        var encoder: MediaCodec? = null
        var inputSurface: Surface? = null
        var muxer: MediaMuxer? = null
        var muxerStarted = false
        var trackIndex = -1

        try {
            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, 2_400_000)
                setInteger(MediaFormat.KEY_FRAME_RATE, fps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }

            encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            inputSurface = encoder.createInputSurface()
            encoder.start()

            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val bufferInfo = MediaCodec.BufferInfo()

            val words = transcript.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            val highlightColor = (subtitleStyle.accentHex and 0xFFFFFFFFL).toInt()

            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = highlightColor }
            val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#0F172A")
                textSize = 26f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 32f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                setShadowLayer(8f, 0f, 3f, Color.BLACK)
            }
            val captionBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(195, 10, 14, 23)
            }
            val captionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 44f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
                setShadowLayer(6f, 0f, 2f, Color.BLACK)
            }
            val frameStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(110, 255, 255, 255)
                style = Paint.Style.STROKE
                strokeWidth = 3f
            }

            var encodedFrameIndex = 0L
            fun drainEncoder(endOfStream: Boolean) {
                if (endOfStream) {
                    encoder.signalEndOfInputStream()
                }
                while (true) {
                    val status = encoder.dequeueOutputBuffer(bufferInfo, 10_000L)
                    when {
                        status == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                            if (!endOfStream) break
                        }
                        status == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            if (!muxerStarted) {
                                trackIndex = muxer.addTrack(encoder.outputFormat)
                                muxer.start()
                                muxerStarted = true
                            }
                        }
                        status >= 0 -> {
                            val encodedData = encoder.getOutputBuffer(status)
                            if (encodedData != null && bufferInfo.size > 0 &&
                                (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0 &&
                                muxerStarted
                            ) {
                                encodedData.position(bufferInfo.offset)
                                encodedData.limit(bufferInfo.offset + bufferInfo.size)
                                bufferInfo.presentationTimeUs = encodedFrameIndex * frameDurationUs
                                encodedFrameIndex++
                                muxer.writeSampleData(trackIndex, encodedData, bufferInfo)
                            }
                            encoder.releaseOutputBuffer(status, false)
                            if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                break
                            }
                        }
                    }
                }
            }

            for (frameIdx in 0 until totalFrames) {
                val progress = frameIdx.toFloat() / max(1, totalFrames - 1).toFloat()
                val canvas: Canvas = inputSurface.lockCanvas(null)
                try {
                    drawFrameToCanvas(
                        canvas = canvas,
                        width = width,
                        height = height,
                        progress = progress,
                        thumbBitmap = thumbBitmap,
                        title = title,
                        stickerText = stickerText,
                        framingMode = framingMode,
                        subtitleStyle = subtitleStyle,
                        words = words,
                        highlightColor = highlightColor,
                        bgPaint = bgPaint,
                        bitmapPaint = bitmapPaint,
                        badgePaint = badgePaint,
                        badgeTextPaint = badgeTextPaint,
                        titlePaint = titlePaint,
                        captionBgPaint = captionBgPaint,
                        captionPaint = captionPaint,
                        frameStrokePaint = frameStrokePaint
                    )
                } finally {
                    inputSurface.unlockCanvasAndPost(canvas)
                }
                drainEncoder(endOfStream = false)
            }

            drainEncoder(endOfStream = true)

            if (muxerStarted) {
                muxer.stop()
                muxerStarted = false
            }

            if (!outputFile.exists() || outputFile.length() == 0L) {
                outputFile.delete()
                return@withContext Result.failure(IllegalStateException("MP4 render produced an empty file"))
            }
            Result.success(outputFile)
        } catch (e: CancellationException) {
            outputFile.delete()
            throw e
        } catch (e: Exception) {
            outputFile.delete()
            Result.failure(e)
        } finally {
            thumbBitmap?.recycle()
            if (muxerStarted) {
                try {
                    muxer?.stop()
                } catch (_: Exception) {
                }
            }
            try {
                muxer?.release()
            } catch (_: Exception) {
            }
            try {
                encoder?.stop()
            } catch (_: Exception) {
            }
            try {
                encoder?.release()
            } catch (_: Exception) {
            }
            try {
                inputSurface?.release()
            } catch (_: Exception) {
            }
        }
    }

    suspend fun saveMp4ToMovies(mp4File: File, clipTitle: String): Result<Uri> =
        withContext(Dispatchers.IO) {
            try {
                if (!mp4File.exists() || mp4File.length() == 0L) {
                    return@withContext Result.failure(IllegalStateException("MP4 file not found"))
                }
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    return@withContext Result.failure(
                        UnsupportedOperationException("Use file save dialog on Android 9 and below")
                    )
                }

                val safeName = clipTitle
                    .replace(Regex("[^a-zA-Z0-9._ -]"), "")
                    .trim()
                    .ifBlank { "ClipForge_Short" }
                    .take(40)
                    .replace(' ', '_')
                val displayName = "${safeName}_${System.currentTimeMillis() % 10000}.mp4"

                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/ClipForge")
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }

                val resolver = context.contentResolver
                val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val destUri = resolver.insert(collection, values)
                    ?: return@withContext Result.failure(IllegalStateException("Could not create MediaStore entry"))

                try {
                    resolver.openOutputStream(destUri)?.use { outStream ->
                        FileInputStream(mp4File).use { inStream ->
                            inStream.copyTo(outStream)
                        }
                    } ?: throw IllegalStateException("Could not open output stream")

                    values.clear()
                    values.put(MediaStore.Video.Media.IS_PENDING, 0)
                    resolver.update(destUri, values, null, null)
                    Result.success(destUri)
                } catch (e: Exception) {
                    resolver.delete(destUri, null, null)
                    throw e
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun copyFileToUri(sourceFile: File, destUri: Uri): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                if (!sourceFile.exists()) {
                    return@withContext Result.failure(IllegalStateException("MP4 file missing"))
                }
                context.contentResolver.openOutputStream(destUri)?.use { outStream ->
                    FileInputStream(sourceFile).use { inStream ->
                        inStream.copyTo(outStream)
                    }
                } ?: return@withContext Result.failure(IllegalStateException("Unable to open destination file"))
                Result.success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private fun drawFrameToCanvas(
        canvas: Canvas,
        width: Int,
        height: Int,
        progress: Float,
        thumbBitmap: Bitmap?,
        title: String,
        stickerText: String,
        framingMode: FramingMode,
        subtitleStyle: SubtitleStyle,
        words: List<String>,
        highlightColor: Int,
        bgPaint: Paint,
        bitmapPaint: Paint,
        badgePaint: Paint,
        badgeTextPaint: Paint,
        titlePaint: Paint,
        captionBgPaint: Paint,
        captionPaint: Paint,
        frameStrokePaint: Paint
    ) {
        val w = width.toFloat()
        val h = height.toFloat()

        // Studio dark gradient backdrop
        bgPaint.shader = LinearGradient(
            0f, 0f, w, h,
            intArrayOf(Color.parseColor("#111827"), Color.parseColor("#1E1B4B"), Color.parseColor("#0A0E17")),
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w, h, bgPaint)

        // Draw thumbnail with smooth Ken Burns zoom motion
        if (thumbBitmap != null && !thumbBitmap.isRecycled) {
            val zoom = 1.0f + (progress * 0.14f)
            val srcW = thumbBitmap.width.toFloat().coerceAtLeast(1f)
            val srcH = thumbBitmap.height.toFloat().coerceAtLeast(1f)
            val scale = if (framingMode == FramingMode.BLURRED_LETTERBOX) {
                (w / srcW) * zoom
            } else {
                max(w / srcW, h / srcH) * zoom
            }
            val dstW = srcW * scale
            val dstH = srcH * scale
            val left = (w - dstW) / 2f
            val top = (h - dstH) / 2f
            val destRect = RectF(left, top, left + dstW, top + dstH)
            canvas.drawBitmap(thumbBitmap, null, destRect, bitmapPaint)
        }

        // Top & bottom contrast scrims for crisp readability
        bgPaint.shader = LinearGradient(
            0f, 0f, 0f, h,
            intArrayOf(Color.argb(175, 0, 0, 0), Color.argb(30, 0, 0, 0), Color.argb(215, 0, 0, 0)),
            floatArrayOf(0f, 0.45f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w, h, bgPaint)
        bgPaint.shader = null

        // Framing overlay guide
        when (framingMode) {
            FramingMode.SPLIT_SCREEN -> canvas.drawLine(0f, h / 2f, w, h / 2f, frameStrokePaint)
            FramingMode.BLURRED_LETTERBOX -> {
                val boxH = w * 9f / 16f
                val top = (h - boxH) / 2f
                canvas.drawRect(12f, top, w - 12f, top + boxH, frameStrokePaint)
            }
            FramingMode.SMART_CENTER -> Unit
        }

        // Top sticker badge
        if (stickerText.isNotBlank()) {
            val label = stickerText.uppercase().take(20)
            val textW = badgeTextPaint.measureText(label)
            val pillRect = RectF(40f, 64f, 40f + textW + 44f, 122f)
            canvas.drawRoundRect(pillRect, 14f, 14f, badgePaint)
            canvas.drawText(label, 62f, 102f, badgeTextPaint)
        }

        // Headline near top
        val cleanTitle = title.take(44)
        if (cleanTitle.isNotBlank()) {
            canvas.drawText(cleanTitle, 40f, 185f, titlePaint)
        }

        // Animated karaoke captions in lower third
        if (words.isNotEmpty()) {
            val activeIdx = (progress * words.size).toInt().coerceIn(0, words.lastIndex)
            val windowStart = (activeIdx - 2).coerceAtLeast(0)
            val windowEnd = (windowStart + 4).coerceAtMost(words.size)
            val windowWords = words.subList(windowStart, windowEnd)
            val formattedWords = windowWords.map {
                if (subtitleStyle == SubtitleStyle.HORMOZI || subtitleStyle == SubtitleStyle.BOLD) it.uppercase() else it
            }
            val fullLine = formattedWords.joinToString(" ")
            val totalLineW = captionPaint.measureText(fullLine).coerceAtMost(w - 96f)
            val boxRect = RectF(
                (w - totalLineW) / 2f - 28f,
                h - 290f,
                (w + totalLineW) / 2f + 28f,
                h - 205f
            )
            canvas.drawRoundRect(boxRect, 18f, 18f, captionBgPaint)

            val spaceW = captionPaint.measureText(" ")
            val wordWidths = formattedWords.map { captionPaint.measureText(it) }
            val lineWidth = wordWidths.sum() + spaceW * (formattedWords.size - 1).coerceAtLeast(0)
            var cursorX = (w - lineWidth) / 2f
            val prevAlign = captionPaint.textAlign
            captionPaint.textAlign = Paint.Align.LEFT
            formattedWords.forEachIndexed { idx, wText ->
                val isActive = (windowStart + idx) == activeIdx
                captionPaint.color = if (isActive) highlightColor else Color.WHITE
                canvas.drawText(wText, cursorX, h - 234f, captionPaint)
                cursorX += wordWidths[idx] + spaceW
            }
            captionPaint.textAlign = prevAlign
        }

        // Subtle progress bar at very bottom
        val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = highlightColor }
        canvas.drawRect(0f, h - 8f, w * progress, h, barPaint)
    }

    private fun downloadThumbnailBitmap(thumbnailUrl: String?): Bitmap? {
        if (thumbnailUrl.isNullOrBlank()) return null
        return try {
            val request = Request.Builder()
                .url(thumbnailUrl)
                .header("User-Agent", "ClipForge/1.0")
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val bytes = response.body?.bytes() ?: return null
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
        } catch (_: Exception) {
            null
        }
    }

    fun getShareableUri(file: File): Uri {
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    suspend fun writeSubtitleFile(fileName: String, content: String): Result<File> =
        withContext(Dispatchers.IO) {
            try {
                val outputDir = File(context.cacheDir, "clips").apply { mkdirs() }
                val file = File(outputDir, fileName)
                file.writeText(content)
                Result.success(file)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private fun readRotationDegrees(uri: Uri): Int {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
        } catch (_: Exception) {
            0
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }
    }

    companion object {
        internal fun mapSampleFlagsToCodecFlags(sampleFlags: Int): Int {
            var codecFlags = 0
            if ((sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0) {
                codecFlags = codecFlags or MediaCodec.BUFFER_FLAG_KEY_FRAME
            }
            if ((sampleFlags and MediaExtractor.SAMPLE_FLAG_PARTIAL_FRAME) != 0) {
                codecFlags = codecFlags or MediaCodec.BUFFER_FLAG_PARTIAL_FRAME
            }
            return codecFlags
        }
    }
}
