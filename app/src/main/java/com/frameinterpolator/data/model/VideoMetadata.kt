package com.frameinterpolator.data.model

import android.net.Uri
import com.frameinterpolator.util.Formatters

/**
 * Represents metadata for a video file
 *
 * @property uri The URI of the video file
 * @property name The display name of the video
 * @property duration The duration of the video in milliseconds
 * @property width The width of the video in pixels
 * @property height The height of the video in pixels
 * @property fps The frame rate of the video
 * @property bitrate The bitrate of the video in bits per second
 * @property size The file size in bytes
 * @property format The MIME type/format of the video
 * @property codec The video codec used
 * @property rotationDegrees The clockwise rotation metadata for the video
 * @property dateAddedMillis The media timestamp when available
 */
data class VideoMetadata(
    val uri: Uri,
    val name: String,
    val duration: Long, // milliseconds
    val width: Int,
    val height: Int,
    val fps: Double,
    val bitrate: Long,
    val size: Long, // bytes
    val format: String,
    val codec: String,
    val rotationDegrees: Int = 0,
    val dateAddedMillis: Long? = null
) {
    val aspectRatio: Double get() = if (height == 0) 1.0 else width.toDouble() / height
    val durationFormatted: String get() = Formatters.formatDuration(duration)
    val sizeFormatted: String get() = Formatters.formatFileSize(size)
}
