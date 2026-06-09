package com.anthonyla.paperize.feature.wallpaper.wallpaper_alarmmanager

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.anthonyla.paperize.core.SettingsConstants
import com.anthonyla.paperize.core.Type
import com.anthonyla.paperize.feature.wallpaper.wallpaper_service.HomeWallpaperService
import com.anthonyla.paperize.feature.wallpaper.wallpaper_service.LockWallpaperService

/**
 * BroadcastReceiver that changes the lock screen wallpaper when the screen turns OFF.
 *
 * Design decisions:
 * - Primary trigger: ACTION_SCREEN_OFF (wallpaper changes while screen is off,
 *   so the new wallpaper is already set when the user turns the screen back on)
 * - Secondary trigger: ACTION_USER_PRESENT as fallback for devices where
 *   SCREEN_OFF broadcast is delayed or not delivered
 * - Debouncing: 3-second minimum interval between wallpaper changes to prevent
 *   rapid successive changes from multiple broadcast events
 * - goAsync() extends the broadcast window for settings reading
 * - startForegroundService() is called BEFORE pendingResult.finish() for Android 12+ compatibility
 */
class UnlockReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "UnlockReceiver"
        private const val PREFS_NAME = "unlock_receiver_prefs"
        private const val LAST_LOCK_STATE = "last_lock_state"
        private const val WAS_DEVICE_LOCKED_BEFORE_SCREEN_OFF = "was_device_locked_before_screen_off"
        private const val LAST_WALLPAPER_CHANGE_TIME = "last_wallpaper_change_time"
        private const val DEBOUNCE_MS = 5000L

        fun registerDynamic(context: Context): UnlockReceiver {
            val receiver = UnlockReceiver()
            val filter = android.content.IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
                } else {
                    context.registerReceiver(receiver, filter)
                }
                Log.d(TAG, "UnlockReceiver dynamically registered")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to dynamically register UnlockReceiver", e)
            }
            return receiver
        }

        fun unregisterDynamic(context: Context, receiver: UnlockReceiver) {
            try {
                context.unregisterReceiver(receiver)
                Log.d(TAG, "UnlockReceiver dynamically unregistered")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to unregister UnlockReceiver", e)
            }
        }

        fun saveLockState(context: Context, isLocked: Boolean) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putBoolean(LAST_LOCK_STATE, isLocked).commit()
        }

        fun getLockState(context: Context): Boolean {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getBoolean(LAST_LOCK_STATE, true)
        }

        fun saveWasLockedBeforeScreenOff(context: Context, wasLocked: Boolean) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putBoolean(WAS_DEVICE_LOCKED_BEFORE_SCREEN_OFF, wasLocked).commit()
        }

        fun getWasLockedBeforeScreenOff(context: Context): Boolean {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getBoolean(WAS_DEVICE_LOCKED_BEFORE_SCREEN_OFF, true)
        }

        internal fun saveLastWallpaperChangeTime(context: Context, timeMs: Long) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putLong(LAST_WALLPAPER_CHANGE_TIME, timeMs).commit()
        }

        private fun getLastWallpaperChangeTime(context: Context): Long {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getLong(LAST_WALLPAPER_CHANGE_TIME, 0L)
        }

        fun isDebounced(context: Context): Boolean {
            val lastChange = getLastWallpaperChangeTime(context)
            val now = System.currentTimeMillis()
            return (now - lastChange) < DEBOUNCE_MS
        }
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        val action = intent.action ?: return
        Log.d(TAG, "Received action: $action")

        when (action) {
            Intent.ACTION_SCREEN_OFF -> {
                // Save current lock state for fallback logic
                try {
                    val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
                    val isLocked = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                        keyguardManager.isDeviceLocked
                    } else {
                        keyguardManager.isKeyguardLocked
                    }
                    saveLockState(context, true)
                    saveWasLockedBeforeScreenOff(context, isLocked)
                    Log.d(TAG, "Screen OFF: isLocked=$isLocked — triggering wallpaper change")
                } catch (e: Exception) {
                    Log.e(TAG, "Error checking keyguard on screen off", e)
                    saveLockState(context, true)
                    saveWasLockedBeforeScreenOff(context, true)
                }

                // Primary trigger: change wallpaper on SCREEN_OFF
                // This ensures the new wallpaper is ready when the user turns the screen back on
                if (!isDebounced(context)) {
                    triggerWallpaperChangeWithDebounce(context)
                } else {
                    Log.d(TAG, "Debounced: skipping wallpaper change on SCREEN_OFF")
                }
            }

            Intent.ACTION_USER_PRESENT -> {
                Log.d(TAG, "Device unlocked (USER_PRESENT) — fallback trigger")
                saveLockState(context, false)
                // Only change on unlock if we haven't recently changed (debounce)
                if (!isDebounced(context)) {
                    triggerWallpaperChangeWithDebounce(context)
                } else {
                    Log.d(TAG, "Debounced: skipping wallpaper change on USER_PRESENT")
                }
            }

            Intent.ACTION_SCREEN_ON -> {
                // Check if device is unlocked after screen on
                // This is a secondary fallback for devices where USER_PRESENT isn't delivered
                try {
                    val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
                    val isLocked = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                        keyguardManager.isDeviceLocked
                    } else {
                        keyguardManager.isKeyguardLocked
                    }

                    saveLockState(context, isLocked)

                    if (!isLocked) {
                        val wasLockedBeforeScreenOff = getWasLockedBeforeScreenOff(context)
                        if (wasLockedBeforeScreenOff || !hasLockScreen(context)) {
                            Log.d(TAG, "Screen ON + unlocked — fallback trigger")
                            if (!isDebounced(context)) {
                                triggerWallpaperChangeWithDebounce(context)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error checking keyguard on SCREEN_ON", e)
                }
            }
        }
    }

    private fun hasLockScreen(context: Context): Boolean {
        return try {
            val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                keyguardManager.isDeviceSecure
            } else {
                keyguardManager.isKeyguardSecure
            }
        } catch (e: Exception) {
            true
        }
    }

    /**
     * Triggers wallpaper change with debounce protection.
     * Records the timestamp immediately to prevent re-triggering within DEBOUNCE_MS.
     * No artificial delay — the wallpaper service starts right away for fast response.
     */
    private fun triggerWallpaperChangeWithDebounce(context: Context) {
        // Record timestamp IMMEDIATELY so any concurrent isDebounced() check sees it
        saveLastWallpaperChangeTime(context, System.currentTimeMillis())
        try {
            handleChangeOnScreenOff(context)
        } catch (e: Exception) {
            Log.e(TAG, "Error in wallpaper change", e)
        }
    }

    /**
     * Reads settings and starts the appropriate wallpaper service.
     * Uses goAsync() to extend the broadcast window and calls
     * startForegroundService() BEFORE pendingResult.finish() to maintain
     * the Android 12+ foreground service exemption.
     */
    private fun handleChangeOnScreenOff(context: Context) {
        val pendingResult = goAsync()

        try {
            val settingsDataStore = com.anthonyla.paperize.data.settings.SettingsDataStoreImpl(context)

            val changeOnUnlock = kotlinx.coroutines.runBlocking {
                settingsDataStore.getBoolean(SettingsConstants.CHANGE_ON_UNLOCK)
            } ?: false

            if (!changeOnUnlock) {
                Log.d(TAG, "Change on unlock/screen-off is disabled, skipping")
                return
            }

            val enableChanger = kotlinx.coroutines.runBlocking {
                settingsDataStore.getBoolean(SettingsConstants.ENABLE_CHANGER)
            } ?: false

            if (!enableChanger) {
                Log.d(TAG, "Wallpaper changer is disabled, skipping")
                return
            }

            val setHome = kotlinx.coroutines.runBlocking {
                settingsDataStore.getBoolean(SettingsConstants.ENABLE_HOME_WALLPAPER)
            } ?: false

            val setLock = kotlinx.coroutines.runBlocking {
                settingsDataStore.getBoolean(SettingsConstants.ENABLE_LOCK_WALLPAPER)
            } ?: false

            val scheduleSeparately = kotlinx.coroutines.runBlocking {
                settingsDataStore.getBoolean(SettingsConstants.SCHEDULE_SEPARATELY)
            } ?: false

            val homeInterval = kotlinx.coroutines.runBlocking {
                settingsDataStore.getInt(SettingsConstants.HOME_WALLPAPER_CHANGE_INTERVAL)
            } ?: SettingsConstants.WALLPAPER_CHANGE_INTERVAL_DEFAULT

            val lockInterval = kotlinx.coroutines.runBlocking {
                settingsDataStore.getInt(SettingsConstants.LOCK_WALLPAPER_CHANGE_INTERVAL)
            } ?: SettingsConstants.WALLPAPER_CHANGE_INTERVAL_DEFAULT

            Log.d(TAG, "Starting wallpaper change: setHome=$setHome, setLock=$setLock, scheduleSeparately=$scheduleSeparately")

            // Start the wallpaper services
            // This must happen BEFORE pendingResult.finish() for Android 12+ compatibility
            if (scheduleSeparately) {
                if (setLock) {
                    tryStartService(context, LockWallpaperService::class.java, LockWallpaperService.Actions.START, homeInterval, lockInterval, true, Type.LOCK.ordinal)
                }
                if (setHome) {
                    tryStartService(context, HomeWallpaperService::class.java, HomeWallpaperService.Actions.START, homeInterval, lockInterval, true, Type.HOME.ordinal)
                }
            } else {
                if (setHome) {
                    tryStartService(context, HomeWallpaperService::class.java, HomeWallpaperService.Actions.START, homeInterval, lockInterval, false, Type.SINGLE.ordinal)
                } else if (setLock) {
                    tryStartService(context, LockWallpaperService::class.java, LockWallpaperService.Actions.START, homeInterval, lockInterval, false, Type.SINGLE.ordinal)
                }
            }

            Log.d(TAG, "Wallpaper change triggered successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error changing wallpaper on screen-off/unlock", e)
        } finally {
            pendingResult.finish()
        }
    }

    private fun tryStartService(
        context: Context,
        serviceClass: Class<*>,
        action: Enum<*>,
        homeInterval: Int,
        lockInterval: Int,
        scheduleSeparately: Boolean,
        type: Int
    ) {
        try {
            val intent = Intent(context, serviceClass).apply {
                this.action = action.toString()
                putExtra("homeInterval", homeInterval)
                putExtra("lockInterval", lockInterval)
                putExtra("scheduleSeparately", scheduleSeparately)
                putExtra("type", type)
            }
            context.startForegroundService(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start ${serviceClass.simpleName}", e)
        }
    }
}
