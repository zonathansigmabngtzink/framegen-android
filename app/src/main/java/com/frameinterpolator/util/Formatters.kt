package com.frameinterpolator.util

import java.text.DateFormat
import java.util.Date
import java.util.Locale

object Formatters {
    fun formatDuration(milliseconds: Long): String {
        val totalSeconds = milliseconds / 1000
        val minutes = totalSeconds / 60
        val hours = minutes / 60
        return when {
            hours > 0 -> String.format(Locale.US, "%d:%02d:%02d", hours, minutes % 60, totalSeconds % 60)
            minutes > 0 -> String.format(Locale.US, "%d:%02d", minutes, totalSeconds % 60)
            else -> String.format(Locale.US, "0:%02d", totalSeconds)
        }
    }

    fun formatFileSize(bytes: Long): String {
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1 -> String.format(Locale.US, "%.2f GB", gb)
            mb >= 1 -> String.format(Locale.US, "%.2f MB", mb)
            kb >= 1 -> String.format(Locale.US, "%.2f KB", kb)
            else -> "$bytes B"
        }
    }

    fun formatDate(timestampMillis: Long): String {
        return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(timestampMillis))
    }
}
