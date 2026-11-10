package teikengstudio.xmpro.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

/** Foreground dataSync service that keeps the download queue alive. */
class DownloadService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createChannel(this)
        val notification = build(this, emptyList())
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        return START_NOT_STICKY
    }

    companion object {
        const val CHANNEL = "teikengstudio.xmpro.downloads"
        private const val NOTIFICATION_ID = 1001

        fun createChannel(context: Context) {
            if (Build.VERSION.SDK_INT < 26) return
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            if (manager.getNotificationChannel(CHANNEL) != null) return
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, "下载进度", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "专辑下载与转码进度"
                    setShowBadge(false)
                },
            )
        }

        fun updateNotification(context: Context, tasks: List<DownloadTask>) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            createChannel(context)
            manager.notify(NOTIFICATION_ID, build(context, tasks))
        }

        private fun build(context: Context, tasks: List<DownloadTask>): Notification {
            val active = tasks.firstOrNull { it.state == DownloadState.downloading }
                ?: tasks.firstOrNull { it.state == DownloadState.queued }
            val done = tasks.count { it.state == DownloadState.completed }
            val total = tasks.size
            val builder = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle(if (active != null) active.title else "下载任务")
                .setContentText(
                    when {
                        active != null -> "已下载 $done/$total 个"
                        else -> "已下载 $done/$total 个"
                    },
                )
                .setOngoing(active != null)
                .setOnlyAlertOnce(true)
            if (active != null && active.totalBytes > 0) {
                builder.setProgress(100, (active.progress * 100).toInt(), false)
            } else {
                builder.setProgress(0, 0, active != null)
            }
            return builder.build()
        }
    }
}
