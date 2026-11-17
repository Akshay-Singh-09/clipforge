package com.example.data.media

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import com.example.model.LocalVideoMeta
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class InspectedLocalVideo(
    val meta: LocalVideoMeta,
    val previewFrames: List<Bitmap>
)

class LocalVideoInspector(private val context: Context) {

    suspend fun inspect(uri: Uri): Result<InspectedLocalVideo> = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 30_000L
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull() ?: 1920
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull() ?: 1080
            val displayName = resolveDisplayName(uri)

            val frames = listOf(0.12, 0.45, 0.78).mapNotNull { fraction ->
                val timeUs = (durationMs * 1000L * fraction).toLong().coerceAtLeast(0L)
                extractBoundedFrame(retriever, timeUs)
            }

            Result.success(
                InspectedLocalVideo(
                    meta = LocalVideoMeta(
                        uriString = uri.toString(),
                        displayName = displayName,
                        durationMs = durationMs,
                        width = width,
                        height = height
                    ),
                    previewFrames = frames
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }
    }

    private fun extractBoundedFrame(retriever: MediaMetadataRetriever, timeUs: Long): Bitmap? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            retriever.getScaledFrameAtTime(
                timeUs,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                720,
                1280
            )
        } else {
            val raw = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: return null
            val maxDim = maxOf(raw.width, raw.height)
            if (maxDim <= 720) {
                raw
            } else {
                val scale = 720f / maxDim.toFloat()
                val targetW = (raw.width * scale).toInt().coerceAtLeast(1)
                val targetH = (raw.height * scale).toInt().coerceAtLeast(1)
                Bitmap.createScaledBitmap(raw, targetW, targetH, true).also {
                    if (it !== raw) raw.recycle()
                }
            }
        }
    }

    private fun resolveDisplayName(uri: Uri): String {
        return try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) cursor.getString(idx) else null
                    } else null
                } ?: uri.lastPathSegment ?: "Picked Video.mp4"
        } catch (_: Exception) {
            uri.lastPathSegment ?: "Picked Video.mp4"
        }
    }
}
