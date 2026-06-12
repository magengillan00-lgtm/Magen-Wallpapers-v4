package com.anthonyla.paperize.feature.wallpaper.wallpaper_alarmmanager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import com.anthonyla.paperize.core.SettingsConstants
import com.anthonyla.paperize.data.settings.SettingsDataStore
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import androidx.core.content.edit

/**
 * Receiver for boot and time change and wallpaper change events to restart alarm manager
 */
@AndroidEntryPoint
class WallpaperBootAndChangeReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_SHORTCUT = "com.anthonyla.paperize.SHORTCUT"
        private const val PREFS_NAME = "wallpaper_receiver_prefs"
        private const val LAST_EXECUTION_TIME = "last_execution_time"
        private const val MIN_INTERVAL_MS = 3000
        private const val WAKELOCK_TAG = "Paperize:BootReceiver"
        private const val WAKELOCK_TIMEOUT_MS = 5 * 60 * 1000L // 5 minutes max
        private const val TAG = "WallpaperBootReceiver"
    }
    @Inject lateinit var settingsDataStoreImpl: SettingsDataStore

    @Suppress("DEPRECATION")
    override fun onReceive(context: Context, intent: Intent) {
        // Acquire a WakeLock to keep the CPU awake during processing
        var wakeLock: PowerManager.WakeLock? = null
        try {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_TAG)
            wakeLock.acquire(WAKELOCK_TIMEOUT_MS)
            Log.d(TAG, "WakeLock acquired for boot/shortcut receiver")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire WakeLock", e)
        }

        goAsync {
            try {
                if (intent.action == ACTION_SHORTCUT && !canExecute(context)) {
                    return@goAsync
                }
                when(intent.action) {
                    Intent.ACTION_BOOT_COMPLETED,
                    Intent.ACTION_TIME_CHANGED,
                    Intent.ACTION_TIMEZONE_CHANGED,
                    ACTION_SHORTCUT -> {
                        saveExecutionTime(context)
                        val scheduler = WallpaperAlarmSchedulerImpl(context, settingsDataStoreImpl)
                        val toggleChanger = settingsDataStoreImpl.getBoolean(SettingsConstants.ENABLE_CHANGER) ?: false
                        val selectedAlbum = settingsDataStoreImpl.getString(SettingsConstants.HOME_ALBUM_NAME) ?: settingsDataStoreImpl.getString(SettingsConstants.LOCK_ALBUM_NAME) ?: ""

                        if (selectedAlbum.isNotEmpty()) {
                            val homeInterval = settingsDataStoreImpl.getInt(SettingsConstants.HOME_WALLPAPER_CHANGE_INTERVAL) ?: SettingsConstants.WALLPAPER_CHANGE_INTERVAL_DEFAULT
                            val lockInterval = settingsDataStoreImpl.getInt(SettingsConstants.LOCK_WALLPAPER_CHANGE_INTERVAL) ?: SettingsConstants.WALLPAPER_CHANGE_INTERVAL_DEFAULT
                            val scheduleSeparately = settingsDataStoreImpl.getBoolean(SettingsConstants.SCHEDULE_SEPARATELY) ?: false
                            val setLock = settingsDataStoreImpl.getBoolean(SettingsConstants.ENABLE_LOCK_WALLPAPER) ?: false
                            val setHome = settingsDataStoreImpl.getBoolean(SettingsConstants.ENABLE_HOME_WALLPAPER) ?: false
                            val changeStartTime = settingsDataStoreImpl.getBoolean(SettingsConstants.CHANGE_START_TIME) ?: false
                            val startHour = settingsDataStoreImpl.getInt(SettingsConstants.START_HOUR) ?: 0
                            val startMinute = settingsDataStoreImpl.getInt(SettingsConstants.START_MINUTE) ?: 0
                            val shuffle = settingsDataStoreImpl.getBoolean(SettingsConstants.SHUFFLE) ?: true
                            val refresh = settingsDataStoreImpl.getBoolean(SettingsConstants.REFRESH) ?: true
                            val alarmItem = WallpaperAlarmItem(
                                homeInterval = homeInterval,
                                lockInterval = lockInterval,
                                scheduleSeparately = scheduleSeparately,
                                setLock = setLock,
                                setHome = setHome,
                                changeStartTime = changeStartTime,
                                startTime = Pair(startHour, startMinute),
                                shuffle = shuffle
                            )
                            alarmItem.let{
                                scheduler.scheduleWallpaperAlarm(
                                    wallpaperAlarmItem = it,
                                    origin = null,
                                    changeImmediate = true,
                                    cancelImmediate = true,
                                    setAlarm = toggleChanger,
                                    homeNextTime = settingsDataStoreImpl.getString(SettingsConstants.HOME_NEXT_SET_TIME),
                                    lockNextTime = settingsDataStoreImpl.getString(SettingsConstants.LOCK_NEXT_SET_TIME),
                                )
                            }
                            WallpaperAlarmSchedulerImpl.scheduleRefresh(context, refresh)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in boot/shortcut receiver", e)
            } finally {
                try {
                    if (wakeLock?.isHeld == true) {
                        wakeLock?.release()
                        Log.d(TAG, "WakeLock released for boot/shortcut receiver")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to release WakeLock", e)
                }
            }
        }
    }

    private fun canExecute(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastExecution = prefs.getLong(LAST_EXECUTION_TIME, 0)
        val currentTime = System.currentTimeMillis()

        return currentTime - lastExecution >= MIN_INTERVAL_MS
    }

    private fun saveExecutionTime(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit() { putLong(LAST_EXECUTION_TIME, System.currentTimeMillis()) }
    }

    /* https://stackoverflow.com/questions/74111692/run-coroutine-functions-on-broadcast-receiver */
    private fun BroadcastReceiver.goAsync(
        context: CoroutineContext = EmptyCoroutineContext,
        block: suspend CoroutineScope.() -> Unit
    ) {
        val pendingResult = goAsync()
        @OptIn(DelicateCoroutinesApi::class)
        GlobalScope.launch(context) {
            try {
                block()
            } finally {
                pendingResult.finish()
            }
        }
    }
}
