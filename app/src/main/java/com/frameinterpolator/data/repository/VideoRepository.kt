package com.frameinterpolator.data.repository

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import com.frameinterpolator.data.model.VideoMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class VideoRepository(private val context: Context) {

    suspend fun getVideoMetadata(uri: Uri): Result<VideoMetadata> = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        val extractor = MediaExtractor()

        try {
            retriever.setDataSource(context, uri)
            extractor.setDataSource(context, uri, emptyMap())

            val videoTrack = findVideoTrack(extractor)
                ?: throw IllegalArgumentException("The selected file does not contain a video track.")

            val mimeType = videoTrack.getString(MediaFormat.KEY_MIME) ?: "video/mp4"
            val duration = when {
                videoTrack.containsKey(MediaFormat.KEY_DURATION) -> videoTrack.getLong(MediaFormat.KEY_DURATION) / 1000L
                else -> retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            }
            val width = videoTrack.getInteger(MediaFormat.KEY_WIDTH)
            val height = videoTrack.getInteger(MediaFormat.KEY_HEIGHT)
            val bitrate = when {
                videoTrack.containsKey(MediaFormat.KEY_BIT_RATE) -> videoTrack.getInteger(MediaFormat.KEY_BIT_RATE).toLong()
                else -> retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull() ?: 0L
            }
            val fps = when {
                videoTrack.containsKey(MediaFormat.KEY_FRAME_RATE) -> videoTrack.getInteger(MediaFormat.KEY_FRAME_RATE).toDouble()
                else -> retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)?.toDoubleOrNull() ?: 30.0
            }
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            val (name, size, dateAddedMillis) = queryFileInfo(uri)

            Result.success(
                VideoMetadata(
                    uri = uri,
                    name = name,
                    duration = duration,
                    width = width,
                    height = height,
                    fps = fps,
                    bitrate = bitrate,
                    size = size,
                    format = mimeType,
                    codec = codecLabelFor(mimeType),
                    rotationDegrees = rotation,
                    dateAddedMillis = dateAddedMillis
                )
            )
        } catch (error: Exception) {
            Result.failure(error)
        } finally {
            extractor.release()
            retriever.release()
        }
    }

    private fun findVideoTrack(extractor: MediaExtractor): MediaFormat? {
        for (index in 0 until extractor.trackCount) {
            val trackFormat = extractor.getTrackFormat(index)
            val mimeType = trackFormat.getString(MediaFormat.KEY_MIME) ?: continue
            if (mimeType.startsWith("video/")) {
                return trackFormat
            }
        }
        return null
    }

    private fun queryFileInfo(uri: Uri): Triple<String, Long, Long?> {
        var displayName = "selected_video"
        var size = 0L
        var dateAddedMillis: Long? = null

        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE, MediaStore.MediaColumns.DATE_ADDED),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameColumn = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeColumn = cursor.getColumnIndex(OpenableColumns.SIZE)
                val dateColumn = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_ADDED)
                if (nameColumn >= 0) {
                    displayName = cursor.getString(nameColumn) ?: displayName
                }
                if (sizeColumn >= 0) {
                    size = cursor.getLong(sizeColumn)
                }
                if (dateColumn >= 0) {
                    dateAddedMillis = cursor.getLong(dateColumn) * 1000L
                }
            }
        }

        return Triple(displayName, size, dateAddedMillis)
    }

    private fun codecLabelFor(mimeType: String): String {
        return when {
            mimeType.contains("avc", ignoreCase = true) || mimeType.contains("h264", ignoreCase = true) -> "H.264"
            mimeType.contains("hevc", ignoreCase = true) || mimeType.contains("h265", ignoreCase = true) -> "H.265"
            mimeType.contains("vp9", ignoreCase = true) -> "VP9"
            mimeType.contains("av1", ignoreCase = true) -> "AV1"
            else -> mimeType.substringAfter('/')
        }
    }
}
