package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R

class StudioForegroundService : Service() {

    private val binder = LocalBinder()
    private var isRunning = false

    inner class LocalBinder : Binder() {
        fun getService(): StudioForegroundService = this@StudioForegroundService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        when (action) {
            ACTION_START -> {
                val hasCamera = intent.getBooleanExtra(EXTRA_HAS_CAMERA, true)
                val hasMic = intent.getBooleanExtra(EXTRA_HAS_MIC, true)
                val hasScreen = intent.getBooleanExtra(EXTRA_HAS_SCREEN, false)
                startForegroundWithTypes(hasCamera, hasMic, hasScreen)
            }
            ACTION_STOP -> {
                stopForegroundService()
            }
        }
        return START_NOT_STICKY
    }

    private fun startForegroundWithTypes(hasCamera: Boolean, hasMic: Boolean, hasScreen: Boolean) {
        if (isRunning) return
        isRunning = true

        val notification = buildNotification("OBS Dok Studio is Active", "Recording or Broadcasting in progress…")

        var serviceType = 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (hasCamera) serviceType = serviceType or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            if (hasMic) serviceType = serviceType or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            if (hasScreen) serviceType = serviceType or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && serviceType != 0) {
            startForeground(NOTIFICATION_ID, notification, serviceType)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    fun updateNotificationText(title: String, content: String) {
        val notification = buildNotification(title, content)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun stopForegroundService() {
        isRunning = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildNotification(title: String, content: String): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, StudioForegroundService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_media_pause, "Stop", stopPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "OBS Dok Studio Active Session",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows live broadcast and recording notification status"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val CHANNEL_ID = "obs_dok_studio_service_channel"
        private const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.example.service.START_STUDIO"
        const val ACTION_STOP = "com.example.service.STOP_STUDIO"
        const val EXTRA_HAS_CAMERA = "extra_has_camera"
        const val EXTRA_HAS_MIC = "extra_has_mic"
        const val EXTRA_HAS_SCREEN = "extra_has_screen"

        fun start(context: Context, hasCamera: Boolean = true, hasMic: Boolean = true, hasScreen: Boolean = false) {
            val intent = Intent(context, StudioForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_HAS_CAMERA, hasCamera)
                putExtra(EXTRA_HAS_MIC, hasMic)
                putExtra(EXTRA_HAS_SCREEN, hasScreen)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, StudioForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
