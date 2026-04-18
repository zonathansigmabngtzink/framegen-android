package com.frameinterpolator.data.export

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.arthenica.ffmpegkit.FFmpegKit
import com.frameinterpolator.MainActivity
import com.frameinterpolator.R
import com.frameinterpolator.data.model.ProcessingState
import com.frameinterpolator.util.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class VideoExportService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val pipeline by lazy { VideoExportPipeline(applicationContext) }
    private var isProcessingQueue = false
    private var finalNotificationText: String? = null

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL -> {
                FFmpegKit.cancel()
                ExportSessionStore.markCancelled()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }

            ACTION_PROCESS_QUEUE -> {
                if (isProcessingQueue) {
                    return START_NOT_STICKY
                }
                isProcessingQueue = true
                finalNotificationText = null
                ExportSessionStore.clearCancelRequest()

                val notification = buildNotification(getString(R.string.notification_preparing))
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        Constants.NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                    )
                } else {
                    startForeground(Constants.NOTIFICATION_ID, notification)
                }

                serviceScope.launch {
                    try {
                        processQueue()
                    } finally {
                        isProcessingQueue = false
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        if (ExportSessionStore.preferences().notifyOnCompletion) {
                            finalNotificationText?.let { text ->
                                getSystemService(NotificationManager::class.java)
                                    .notify(Constants.NOTIFICATION_ID + 1, buildCompletionNotification(text))
                            }
                        }
                        stopSelf()
                    }
                }
            }
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private suspend fun processQueue() {
        while (true) {
            val job = ExportSessionStore.consumeNextJob() ?: break
            ExportSessionStore.updateProcessingState(ProcessingState.Preparing)
            updateNotification(getString(R.string.notification_preparing), 0f)

            try {
                val result = pipeline.export(job.metadata, job.config) { state ->
                    if (state is ProcessingState.Running) {
                        updateNotification(state.stageLabel, state.progress)
                    }
                    ExportSessionStore.updateProcessingState(state)
                }
                ExportSessionStore.markCompleted(result.outputUri, result.displayName)
                updateNotification(getString(R.string.notification_saved), 1f)
                finalNotificationText = getString(R.string.notification_saved)
            } catch (_: ExportCancelledException) {
                ExportSessionStore.markCancelled()
                finalNotificationText = getString(R.string.notification_cancelled)
                break
            } catch (error: ExportFailedException) {
                ExportSessionStore.markFailed(
                    message = error.message,
                    ffmpegExitCode = error.ffmpegExitCode
                )
                updateNotification(getString(R.string.notification_failed), null)
                finalNotificationText = getString(R.string.notification_failed)
                continue
            } catch (error: Exception) {
                ExportSessionStore.markFailed(error.message ?: getString(R.string.error_export_failed))
                updateNotification(getString(R.string.notification_failed), null)
                finalNotificationText = getString(R.string.notification_failed)
                continue
            }
        }
    }

    private fun updateNotification(title: String, progress: Float?) {
        getSystemService(NotificationManager::class.java)
            .notify(Constants.NOTIFICATION_ID, buildNotification(title, progress))
    }

    private fun buildNotification(title: String, progress: Float? = null): Notification {
        val activityIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val cancelIntent = PendingIntent.getService(
            this,
            1,
            createCancelIntent(this),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, Constants.NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.export_notification_title))
            .setContentText(title)
            .setOnlyAlertOnce(true)
            .setOngoing(progress != null)
            .setContentIntent(activityIntent)
            .addAction(0, getString(R.string.cancel), cancelIntent)
            .setProgress(
                100,
                progress?.times(100)?.toInt()?.coerceIn(0, 100) ?: 0,
                progress == null
            )
            .build()
    }

    private fun buildCompletionNotification(message: String): Notification {
        val activityIntent = PendingIntent.getActivity(
            this,
            2,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, Constants.NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(message)
            .setAutoCancel(true)
            .setContentIntent(activityIntent)
            .build()
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                Constants.NOTIFICATION_CHANNEL_ID,
                Constants.NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val ACTION_PROCESS_QUEUE = "com.frameinterpolator.action.PROCESS_QUEUE"
        private const val ACTION_CANCEL = "com.frameinterpolator.action.CANCEL_EXPORT"

        fun createProcessQueueIntent(context: Context): Intent {
            return Intent(context, VideoExportService::class.java).apply {
                action = ACTION_PROCESS_QUEUE
            }
        }

        fun createCancelIntent(context: Context): Intent {
            return Intent(context, VideoExportService::class.java).apply {
                action = ACTION_CANCEL
            }
        }
    }
}
