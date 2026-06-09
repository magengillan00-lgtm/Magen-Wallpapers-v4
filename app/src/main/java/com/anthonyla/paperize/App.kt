package com.anthonyla.paperize

import android.app.Application
import android.app.KeyguardManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.database.CursorWindow
import android.os.Build
import android.util.Log
import com.anthonyla.paperize.core.SettingsConstants
import com.anthonyla.paperize.data.settings.SettingsDataStoreImpl
import com.anthonyla.paperize.feature.wallpaper.wallpaper_alarmmanager.UnlockReceiver
import dagger.hilt.android.HiltAndroidApp
import java.lang.reflect.Field
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Application class for Hilt
 */
@HiltAndroidApp
class App: Application() {
    companion object {
        private const val TAG = "PaperizeApp"
    }

    // Dynamic receiver for unlock detection (required for Honor/Huawei devices)
    @Volatile
    private var unlockReceiver: UnlockReceiver? = null

    // Settings data store for checking preferences
    private lateinit var settingsDataStoreImpl: SettingsDataStoreImpl

    override fun onCreate() {
        super.onCreate()

        settingsDataStoreImpl = SettingsDataStoreImpl(this)

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

        // Initialize lock state from current device state
        val keyguardManager = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
        val isCurrentlyLocked = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            keyguardManager.isDeviceLocked
        } else {
            keyguardManager.isKeyguardLocked
        }
        UnlockReceiver.saveLockState(this, isCurrentlyLocked)
        UnlockReceiver.saveWasLockedBeforeScreenOff(this, isCurrentlyLocked)
        Log.d(TAG, "App started: isCurrentlyLocked=$isCurrentlyLocked")

        // Register dynamic receiver for unlock detection
        // This is CRITICAL for Honor/Huawei devices where manifest-registered
        // receivers for ACTION_USER_PRESENT don't work
        registerUnlockReceiverIfNeeded()
    }

    /**
     * Register the dynamic UnlockReceiver if the "change on unlock" setting is enabled.
     * This should also be called when the setting changes.
     */
    fun registerUnlockReceiverIfNeeded() {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val changeOnUnlock = settingsDataStoreImpl.getBoolean(SettingsConstants.CHANGE_ON_UNLOCK) ?: false
                val enableChanger = settingsDataStoreImpl.getBoolean(SettingsConstants.ENABLE_CHANGER) ?: false

                if (changeOnUnlock && enableChanger) {
                    registerUnlockReceiver()
                } else {
                    unregisterUnlockReceiver()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking unlock setting", e)
                // Register anyway as a safe default
                registerUnlockReceiver()
            }
        }
    }

    /**
     * Force register the unlock receiver (called when setting is toggled on).
     * Must be called on the main thread for receiver registration.
     */
    @Synchronized
    fun registerUnlockReceiver() {
        if (unlockReceiver != null) {
            Log.d(TAG, "UnlockReceiver already registered")
            return
        }
        try {
            unlockReceiver = UnlockReceiver.registerDynamic(this)
            Log.d(TAG, "UnlockReceiver registered successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register UnlockReceiver", e)
        }
    }

    /**
     * Force unregister the unlock receiver (called when setting is toggled off).
     */
    @Synchronized
    fun unregisterUnlockReceiver() {
        val currentReceiver = unlockReceiver
        if (currentReceiver == null) {
            Log.d(TAG, "UnlockReceiver not registered, nothing to unregister")
            return
        }
        try {
            UnlockReceiver.unregisterDynamic(this, currentReceiver)
            unlockReceiver = null
            Log.d(TAG, "UnlockReceiver unregistered successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unregister UnlockReceiver", e)
            unlockReceiver = null
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        unregisterUnlockReceiver()
    }
}
