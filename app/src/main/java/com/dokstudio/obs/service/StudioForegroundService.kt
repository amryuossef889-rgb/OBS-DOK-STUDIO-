package com.dokstudio.obs.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.Build
import androidx.core.app.ServiceCompat
import androidx.core.app.NotificationCompat

class StudioForegroundService : Service() {
    private var foregroundReady = false
    private val readyCallbacks = mutableListOf<() -> Unit>()
    private val binder = LocalBinder()

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Studio capture", NotificationManager.IMPORTANCE_LOW),
        )
        lastBinder = binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            foregroundReady = false
            readyCallbacks.clear()
            if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelfResult(startId)
            return START_NOT_STICKY
        }

        if (intent?.action == ACTION_START) {
            val camera = intent.getBooleanExtra(EXTRA_CAMERA, false)
            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle("OBS Dok Studio")
                .setContentText(
                    if (camera) "Screen, camera and microphone capture active"
                    else "Screen and microphone capture active"
                )
                .setOngoing(true)
                .build()

            val types =
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION or
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or
                    if (camera) android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA else 0

            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, types)
            foregroundReady = true
            readyCallbacks.toList().forEach { it() }
            readyCallbacks.clear()
        }
        return START_NOT_STICKY
    }

    inner class LocalBinder : Binder() {
        fun awaitReady(callback: () -> Unit) {
            if (foregroundReady) callback() else readyCallbacks += callback
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        foregroundReady = false
        readyCallbacks.clear()
        if (lastBinder === binder) lastBinder = null
        super.onDestroy()
    }

    companion object {
        @Volatile
        var lastBinder: LocalBinder? = null

        const val ACTION_START = "com.dokstudio.obs.action.START_CAPTURE"
        const val ACTION_STOP = "com.dokstudio.obs.action.STOP_CAPTURE"
        const val EXTRA_CAMERA = "com.dokstudio.obs.extra.CAMERA"
        private const val CHANNEL_ID = "studio_capture"
        private const val NOTIFICATION_ID = 42
    }
}
