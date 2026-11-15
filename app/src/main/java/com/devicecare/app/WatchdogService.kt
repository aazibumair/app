package com.devicecare.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import androidx.core.app.NotificationCompat

/**
 * Keeps the protection engine switched on. If someone turns the accessibility service off,
 * this turns it straight back on (needs the one-time WRITE_SECURE_SETTINGS grant).
 */
class WatchdogService : Service() {

    private var observer: ContentObserver? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel("care", "Device Care", NotificationManager.IMPORTANCE_MIN)
        )
        val n: Notification = NotificationCompat.Builder(this, "care")
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentTitle("Device Care")
            .setContentText("Active")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, n)
        }

        val obs = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                repair()
            }
        }
        observer = obs
        contentResolver.registerContentObserver(
            Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES),
            false,
            obs
        )
        repair()
    }

    private fun repair() {
        if (Config.enabled(this) && !Access.isEnabled(this)) {
            Access.enable(this)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        repair()
        return START_STICKY
    }

    override fun onDestroy() {
        observer?.let { contentResolver.unregisterContentObserver(it) }
        super.onDestroy()
    }
}
