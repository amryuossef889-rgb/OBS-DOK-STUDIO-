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
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Studio capture", NotificationManager.IMPORTANCE_LOW),
        )
        manager.createNotificationChannel(
            NotificationChannel(ERROR_CHANNEL_ID, "Studio recording events", NotificationManager.IMPORTANCE_DEFAULT),
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_ERROR) {
            val reason = intent.getStringExtra(EXTRA_ERROR) ?: "Recording stopped unexpectedly"
            val notification = NotificationCompat.Builder(this, ERROR_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("OBS Dok Studio — Recording stopped")
                .setContentText(reason)
                .setStyle(NotificationCompat.BigTextStyle().bigText(reason))
                .setAutoCancel(true)
                .build()
            getSystemService(NotificationManager::class.java).notify(ERROR_NOTIFICATION_ID, notification)
            foregroundReady = false
            readyCallbacks.clear()
            if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelfResult(startId)
            return START_NOT_STICKY
        }

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
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.dokstudio.obs.action.START_CAPTURE"
        const val ACTION_STOP = "com.dokstudio.obs.action.STOP_CAPTURE"
        const val ACTION_ERROR = "com.dokstudio.obs.action.RECORDING_ERROR"
        const val EXTRA_CAMERA = "com.dokstudio.obs.extra.CAMERA"
        const val EXTRA_ERROR = "com.dokstudio.obs.extra.ERROR"
        private const val CHANNEL_ID = "studio_capture"
        private const val ERROR_CHANNEL_ID = "studio_recording_events"
        private const val NOTIFICATION_ID = 42
        private const val ERROR_NOTIFICATION_ID = 43
    }
}
