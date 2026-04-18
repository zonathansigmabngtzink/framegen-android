package com.frameinterpolator.util

import android.app.ActivityManager
import android.content.Context
import android.os.Debug
import java.io.RandomAccessFile

/**
 * System statistics collector for monitoring CPU, RAM, etc during processing
 */
class SystemStats(private val context: Context) {

    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private var lastCpuTime = 0L
    private var lastSystemTime = 0L

    /**
     * Get current CPU usage percentage (0-100)
     */
    fun getCpuUsage(): Int {
        return try {
            val reader = RandomAccessFile("/proc/stat", "r")
            val load = reader.readLine()
            reader.close()

            val toks = load.substring(5).trim().split(" +".toRegex())
            val idle = toks[3].toLong()
            val total = toks.sumOf { it.toLongOrNull() ?: 0L }

            val usage = if (lastSystemTime > 0) {
                val totalDelta = total - lastSystemTime
                val idleDelta = idle - lastCpuTime
                ((totalDelta - idleDelta) * 100.0 / totalDelta).toInt()
            } else {
                0
            }

            lastSystemTime = total
            lastCpuTime = idle

            usage.coerceIn(0, 100)
        } catch (e: Exception) {
            0
        }
    }

    /**
     * Get current memory usage in MB
     */
    fun getMemoryUsageMB(): Long {
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)

        val nativeHeap = Debug.getNativeHeapAllocatedSize()
        val dalvikHeap = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()

        return (nativeHeap + dalvikHeap) / (1024 * 1024)
    }

    /**
     * Get total available memory in MB
     */
    fun getTotalMemoryMB(): Long {
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        return memInfo.totalMem / (1024 * 1024)
    }

    /**
     * Get available memory in MB
     */
    fun getAvailableMemoryMB(): Long {
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        return memInfo.availMem / (1024 * 1024)
    }

    /**
     * Check if system is under memory pressure
     */
    fun isLowMemory(): Boolean {
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        return memInfo.lowMemory
    }

    data class Stats(
        val cpuUsage: Int,
        val memoryUsedMB: Long,
        val memoryAvailableMB: Long,
        val memoryTotalMB: Long,
        val isLowMemory: Boolean
    )

    /**
     * Get all stats at once
     */
    fun getAllStats(): Stats {
        return Stats(
            cpuUsage = getCpuUsage(),
            memoryUsedMB = getMemoryUsageMB(),
            memoryAvailableMB = getAvailableMemoryMB(),
            memoryTotalMB = getTotalMemoryMB(),
            isLowMemory = isLowMemory()
        )
    }
}
