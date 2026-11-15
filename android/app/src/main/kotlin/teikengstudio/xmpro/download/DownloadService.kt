package teikengstudio.xmpro.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat

/**
 * Keeps the download queue alive while the app is in the background.
 *
 * Android stops ordinary work once the app leaves the foreground, so the queue
 * runs under a `dataSync` foreground service. The notification is deliberately
 * quiet: it is a progress note, not something to interact with.
 */
class DownloadService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, notification())
        return START_NOT_STICKY
    }

    private fun notification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, "离线下载", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "下载音频时显示进度"
                    setShowBadge(false)
                },
            )
        }
        return NotificationCompat.Builder(this, CHANNEL)
            .setContentTitle("正在下载音频")
            .setContentText("下载完成后可离线收听")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        private const val CHANNEL = "downloads"
        private const val NOTIFICATION_ID = 0x0D1

        fun start(context: Context) {
            runCatching {
                context.startForegroundService(Intent(context, DownloadService::class.java))
            }
        }

        fun stop(context: Context) {
            runCatching {
                context.stopService(Intent(context, DownloadService::class.java))
            }
        }
    }
}
