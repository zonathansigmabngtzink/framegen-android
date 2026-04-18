package com.frameinterpolator.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File

fun Uri.getFileName(context: Context): String {
    var fileName = "selected_video"
    context.contentResolver.query(this, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (cursor.moveToFirst() && nameIndex >= 0) {
            fileName = cursor.getString(nameIndex) ?: fileName
        }
    }
    return fileName
}

fun Uri.getFileSize(context: Context): Long {
    var size = 0L
    context.contentResolver.query(this, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
        if (cursor.moveToFirst() && sizeIndex >= 0) {
            size = cursor.getLong(sizeIndex)
        }
    }
    return size
}

/**
 * Coerce value between min and max
 */
fun <T : Comparable<T>> T.coerceIn(min: T, max: T): T {
    return when {
        this < min -> min
        this > max -> max
        else -> this
    }
}

/**
 * Delete directory recursively
 */
fun File.deleteRecursively(): Boolean {
    return if (isDirectory) {
        listFiles()?.forEach { it.deleteRecursively() }
        delete()
    } else {
        delete()
    }
}

/**
 * Get available storage space in bytes
 */
fun File.getAvailableSpace(): Long {
    return usableSpace
}
