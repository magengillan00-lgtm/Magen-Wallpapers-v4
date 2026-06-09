package com.anthonyla.paperize

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.database.CursorWindow
import java.lang.reflect.Field
import dagger.hilt.android.HiltAndroidApp

/**
 * Application class for Hilt
 */
@HiltAndroidApp
class App: Application() {
    companion object {
        private const val TAG = "PaperizeApp"
    }

    override fun onCreate() {
        super.onCreate()

        // Increase CursorWindow size to handle large datasets
        // Default is usually 2MB, increasing to 10MB
        try {
            val field: Field = CursorWindow::class.java.getDeclaredField("sCursorWindowSize")
            field.isAccessible = true
            field.set(null, 10 * 1024 * 1024) // 10MB
        } catch (e: Exception) {
            // Fallback for newer Android versions where field might be different
            try {
                val field: Field = CursorWindow::class.java.getDeclaredField("CURSOR_WINDOW_SIZE")
                field.isAccessible = true
                field.set(null, 10 * 1024 * 1024) // 10MB
            } catch (e2: Exception) {
                e2.printStackTrace()
            }
        }

        val channel = NotificationChannel("wallpaper_service_channel", "AutoWallpaperChangerPro", NotificationManager.IMPORTANCE_MIN).apply {
            setSound(null, null)
            setShowBadge(false)
            description = "Silent service notification"
        }
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }
}
