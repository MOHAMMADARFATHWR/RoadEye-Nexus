package com.example

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
import androidx.core.content.ContextCompat

class TelemetryForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                TelemetrySessionController.getInstance(applicationContext).stopStreaming()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_START, null -> {
                createNotificationChannel()
                val endpoint = intent?.getStringExtra(EXTRA_ENDPOINT) ?: "UDP Node"
                val rateHz = intent?.getIntExtra(EXTRA_RATE_HZ, 20) ?: 20
                val notification = buildNotification(endpoint, rateHz)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
                return START_STICKY
            }

            else -> return START_NOT_STICKY
        }
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.fgs_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Keeps RoadEye Nexus 20Hz UDP telemetry active in the background"
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(endpoint: String, rateHz: Int): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPending = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val stopIntent = Intent(this, TelemetryForegroundService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPending = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.fgs_notification_title))
            .setContentText("Broadcasting at ${rateHz}Hz to $endpoint")
            .setSmallIcon(R.drawable.ic_stat_roadeye)
            .setOngoing(true)
            .setContentIntent(openAppPending)
            .addAction(android.R.drawable.ic_media_pause, "Halt Stream", stopPending)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "roadeye_telemetry_stream_channel"
        private const val NOTIFICATION_ID = 4091
        private const val ACTION_START = "com.example.action.START_TELEMETRY_FGS"
        private const val ACTION_STOP = "com.example.action.STOP_TELEMETRY_FGS"
        private const val EXTRA_ENDPOINT = "extra_endpoint"
        private const val EXTRA_RATE_HZ = "extra_rate_hz"

        fun start(context: Context, endpoint: String, rateHz: Int) {
            val intent = Intent(context, TelemetryForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_ENDPOINT, endpoint)
                putExtra(EXTRA_RATE_HZ, rateHz)
            }
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (_: Exception) {
                // Safe fallback if system restricts background FGS start.
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, TelemetryForegroundService::class.java)
            context.stopService(intent)
        }
    }
}
