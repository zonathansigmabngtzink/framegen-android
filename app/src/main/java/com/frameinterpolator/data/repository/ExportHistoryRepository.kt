package com.frameinterpolator.data.repository

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import com.frameinterpolator.data.model.ExportHistoryItem
import com.frameinterpolator.util.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ExportHistoryRepository(
    private val context: Context
) {
    suspend fun loadHistory(limit: Int = 50): List<ExportHistoryItem> = withContext(Dispatchers.IO) {
        val history = mutableListOf<ExportHistoryItem>()
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.RELATIVE_PATH
        )

        val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"
        context.contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val widthColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
            val heightColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
            val pathColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.RELATIVE_PATH)

            while (cursor.moveToNext() && history.size < limit) {
                val relativePath = cursor.getString(pathColumn).orEmpty()
                if (!relativePath.contains(Constants.OUTPUT_DIRECTORY, ignoreCase = true)) {
                    continue
                }

                val id = cursor.getLong(idColumn)
                val displayName = cursor.getString(nameColumn).orEmpty()
                history += ExportHistoryItem(
                    uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id),
                    displayName = displayName,
                    targetFps = parseTargetFps(displayName),
                    width = cursor.getInt(widthColumn),
                    height = cursor.getInt(heightColumn),
                    sizeBytes = cursor.getLong(sizeColumn),
                    durationMillis = cursor.getLong(durationColumn),
                    dateAddedMillis = cursor.getLong(dateColumn) * 1000L
                )
            }
        }

        history
    }

    private fun parseTargetFps(displayName: String): Int? {
        return Regex("_(\\d+)fps_", RegexOption.IGNORE_CASE)
            .find(displayName)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
    }
}
