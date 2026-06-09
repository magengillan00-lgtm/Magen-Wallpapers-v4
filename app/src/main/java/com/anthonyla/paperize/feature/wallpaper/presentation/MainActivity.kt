package com.anthonyla.paperize.feature.wallpaper.presentation

import android.Manifest
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.animation.AccelerateInterpolator
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.RequiresPermission
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.animation.doOnEnd
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.anthonyla.paperize.App
import com.anthonyla.paperize.core.SettingsConstants
import com.anthonyla.paperize.core.Type
import com.anthonyla.paperize.data.settings.SettingsDataStore
import com.anthonyla.paperize.feature.wallpaper.presentation.settings_screen.SettingsEvent
import com.anthonyla.paperize.feature.wallpaper.presentation.settings_screen.SettingsState
import com.anthonyla.paperize.feature.wallpaper.presentation.settings_screen.SettingsViewModel
import com.anthonyla.paperize.feature.wallpaper.presentation.themes.AutoWallpaperChangerProTheme
import com.anthonyla.paperize.feature.wallpaper.wallpaper_alarmmanager.UnlockReceiver
import com.anthonyla.paperize.feature.wallpaper.wallpaper_alarmmanager.WallpaperAlarmItem
import com.anthonyla.paperize.feature.wallpaper.wallpaper_alarmmanager.WallpaperAlarmSchedulerImpl
import com.anthonyla.paperize.feature.wallpaper.wallpaper_alarmmanager.WallpaperReceiver
import com.anthonyla.paperize.feature.wallpaper.wallpaper_service.HomeWallpaperService
import com.anthonyla.paperize.feature.wallpaper.wallpaper_service.LockWallpaperService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var settingsDataStoreImpl: SettingsDataStore
    private val settingsViewModel: SettingsViewModel by viewModels()

    // Launcher for SET_WALLPAPER permission
    private val requestSetWallpaperPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (!isGranted) {
            // Handle the case where the user denies the permission.
        }
    }

    // Launcher for POST_NOTIFICATIONS permission (Android 13+)
    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        Log.d("MainActivity", "POST_NOTIFICATIONS permission: $isGranted")
    }

    // Launcher for SCHEDULE_EXACT_ALARM permission (Android 12+)
    private val requestExactAlarmPermission = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // No specific action needed on result here
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // installSplashScreen() MUST be called before super.onCreate()
        val splashScreen = installSplashScreen()

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)

        // Request SET_WALLPAPER permission immediately if not granted
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SET_WALLPAPER) != PackageManager.PERMISSION_GRANTED) {
            requestSetWallpaperPermission.launch(Manifest.permission.SET_WALLPAPER)
        }

        // Request POST_NOTIFICATIONS permission on Android 13+ (required for foreground service)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // Trigger a refresh of wallpapers when the app is opened.
        try {
            val refreshIntent = Intent(this, HomeWallpaperService::class.java).apply {
                action = HomeWallpaperService.Actions.REFRESH.toString()
            }
            startForegroundService(refreshIntent)
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to start refresh service", e)
        }

        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.R) {
            splashScreen.setOnExitAnimationListener { splashScreenViewProvider ->
                val fadeOut = ObjectAnimator.ofFloat(splashScreenViewProvider.view, View.ALPHA, 1f, 0f)
                fadeOut.interpolator = AccelerateInterpolator()
                fadeOut.duration = 300L
                fadeOut.doOnEnd { splashScreenViewProvider.remove() }
                fadeOut.start()
            }
        }
        splashScreen.setKeepOnScreenCondition { settingsViewModel.setKeepOnScreenCondition }

        setContent {
            // Request SCHEDULE_EXACT_ALARM permission when the Composable is active.
            LaunchedEffect(Unit) {
                requestExactAlarmPermissionIfNeeded(this@MainActivity)
            }

            val settingsState = settingsViewModel.state.collectAsStateWithLifecycle()
            val isFirstLaunch by settingsDataStoreImpl.getBooleanFlow(SettingsConstants.FIRST_LAUNCH)
                .collectAsStateWithLifecycle(initialValue = true)
            val scheduler = WallpaperAlarmSchedulerImpl(this, settingsDataStoreImpl)

            var hasScheduleRun by remember { mutableStateOf(false) }
            LaunchedEffect(settingsState.value) {
                // Only attempt to schedule once settings are initialized and the scheduling logic hasn't run yet.
                if (!hasScheduleRun && settingsState.value.initialized) {
                    handleWallpaperScheduling(settingsState.value, scheduler)
                    hasScheduleRun = true
                }
            }

            // Register/unlock receiver based on settings changes
            LaunchedEffect(
                settingsState.value.scheduleSettings.changeOnUnlock,
                settingsState.value.wallpaperSettings.enableChanger
            ) {
                val app = application as? App
                val changeOnUnlock = settingsState.value.scheduleSettings.changeOnUnlock
                val enableChanger = settingsState.value.wallpaperSettings.enableChanger
                if (app != null) {
                    if (changeOnUnlock && enableChanger) {
                        app.registerUnlockReceiver()
                    } else {
                        app.unregisterUnlockReceiver()
                    }
                }
            }

            AutoWallpaperChangerProTheme(
                darkMode = settingsState.value.themeSettings.darkMode,
                amoledMode = settingsState.value.themeSettings.amoledTheme,
                dynamicTheming = settingsState.value.themeSettings.dynamicTheming
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    tonalElevation = 5.dp
                ) {
                    AutoWallpaperChangerProApp(isFirstLaunch ?: true, scheduler)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        handleUnlockChangeIfNeeded()
        // Re-register the dynamic receiver if needed (in case process was killed)
        (application as? App)?.registerUnlockReceiverIfNeeded()
    }

    override fun onPause() {
        super.onPause()
        // Update lock state when activity is paused (e.g., user pressed home or screen turned off)
        // This ensures we track when the device gets locked
        try {
            val keyguardManager = getSystemService(KEYGUARD_SERVICE) as android.app.KeyguardManager
            val isLocked = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                keyguardManager.isDeviceLocked
            } else {
                keyguardManager.isKeyguardLocked
            }
            UnlockReceiver.saveLockState(this, isLocked)
        } catch (e: Exception) {
            Log.e("MainActivity", "Error checking lock state in onPause", e)
        }
    }

    override fun onStop() {
        super.onStop()
        // When activity stops, assume device will be locked next time
        // This ensures we detect unlock even after prolonged background time
        UnlockReceiver.saveLockState(this, true)
        UnlockReceiver.saveWasLockedBeforeScreenOff(this, true)
    }

    /**
     * Fallback mechanism for detecting device unlock on devices where
     * ACTION_USER_PRESENT broadcast is not delivered (e.g., Honor/Huawei devices).
     * This checks the keyguard state when the activity resumes and triggers
     * a wallpaper change if the device was just unlocked.
     *
     * This uses SharedPreferences (via UnlockReceiver companion) to persist
     * lock state across process kills, which is essential for Honor devices
     * that aggressively kill background processes.
     */
    private fun handleUnlockChangeIfNeeded() {
        try {
            val keyguardManager = getSystemService(KEYGUARD_SERVICE) as android.app.KeyguardManager
            val isCurrentlyLocked = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                keyguardManager.isDeviceLocked
            } else {
                keyguardManager.isKeyguardLocked
            }

            val wasDeviceLocked = UnlockReceiver.getLockState(this)

            Log.d("MainActivity", "handleUnlockChangeIfNeeded: wasDeviceLocked=$wasDeviceLocked, isCurrentlyLocked=$isCurrentlyLocked")

            // If the device was previously locked and now it's not, it was just unlocked
            if (wasDeviceLocked && !isCurrentlyLocked) {
                // CRITICAL: Check debouncing to prevent multiple wallpaper changes
                if (UnlockReceiver.isDebounced(this)) {
                    Log.d("MainActivity", "Debounced: skipping wallpaper change in handleUnlockChangeIfNeeded")
                    UnlockReceiver.saveLockState(this, isCurrentlyLocked)
                    return
                }

                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val changeOnUnlock = settingsDataStoreImpl.getBoolean(SettingsConstants.CHANGE_ON_UNLOCK) ?: false
                        if (!changeOnUnlock) {
                            Log.d("MainActivity", "Change on unlock is disabled, skipping")
                            return@launch
                        }

                        val enableChanger = settingsDataStoreImpl.getBoolean(SettingsConstants.ENABLE_CHANGER) ?: false
                        if (!enableChanger) {
                            Log.d("MainActivity", "Wallpaper changer is disabled, skipping unlock change")
                            return@launch
                        }

                        // Record the wallpaper change time to prevent UnlockReceiver from re-triggering
                        UnlockReceiver.saveLastWallpaperChangeTime(this@MainActivity, System.currentTimeMillis())

                        triggerWallpaperChangeOnUnlock()
                    } catch (e: Exception) {
                        Log.e("MainActivity", "Error changing wallpaper on unlock (onResume)", e)
                    }
                }
            }

            // Update lock state tracking
            UnlockReceiver.saveLockState(this, isCurrentlyLocked)
        } catch (e: Exception) {
            Log.e("MainActivity", "Error in handleUnlockChangeIfNeeded", e)
        }
    }

    /**
     * Triggers the wallpaper change services for the unlock event.
     */
    private suspend fun triggerWallpaperChangeOnUnlock() {
        val setHome = settingsDataStoreImpl.getBoolean(SettingsConstants.ENABLE_HOME_WALLPAPER) ?: false
        val setLock = settingsDataStoreImpl.getBoolean(SettingsConstants.ENABLE_LOCK_WALLPAPER) ?: false
        val scheduleSeparately = settingsDataStoreImpl.getBoolean(SettingsConstants.SCHEDULE_SEPARATELY) ?: false
        val homeInterval = settingsDataStoreImpl.getInt(SettingsConstants.HOME_WALLPAPER_CHANGE_INTERVAL)
            ?: SettingsConstants.WALLPAPER_CHANGE_INTERVAL_DEFAULT
        val lockInterval = settingsDataStoreImpl.getInt(SettingsConstants.LOCK_WALLPAPER_CHANGE_INTERVAL)
            ?: SettingsConstants.WALLPAPER_CHANGE_INTERVAL_DEFAULT

        Log.d("MainActivity", "Triggering wallpaper change on unlock: setHome=$setHome, setLock=$setLock, scheduleSeparately=$scheduleSeparately")

        withContext(Dispatchers.Main) {
            try {
                if (scheduleSeparately) {
                    if (setLock) {
                        val lockIntent = Intent(this@MainActivity, LockWallpaperService::class.java).apply {
                            action = LockWallpaperService.Actions.START.toString()
                            putExtra("homeInterval", homeInterval)
                            putExtra("lockInterval", lockInterval)
                            putExtra("scheduleSeparately", true)
                            putExtra("type", Type.LOCK.ordinal)
                        }
                        startForegroundService(lockIntent)
                    }
                    if (setHome) {
                        val homeIntent = Intent(this@MainActivity, HomeWallpaperService::class.java).apply {
                            action = HomeWallpaperService.Actions.START.toString()
                            putExtra("homeInterval", homeInterval)
                            putExtra("lockInterval", lockInterval)
                            putExtra("scheduleSeparately", true)
                            putExtra("type", Type.HOME.ordinal)
                        }
                        startForegroundService(homeIntent)
                    }
                } else {
                    if (setHome) {
                        val homeIntent = Intent(this@MainActivity, HomeWallpaperService::class.java).apply {
                            action = HomeWallpaperService.Actions.START.toString()
                            putExtra("homeInterval", homeInterval)
                            putExtra("lockInterval", lockInterval)
                            putExtra("scheduleSeparately", false)
                            putExtra("type", Type.SINGLE.ordinal)
                        }
                        startForegroundService(homeIntent)
                    } else if (setLock) {
                        val lockIntent = Intent(this@MainActivity, LockWallpaperService::class.java).apply {
                            action = LockWallpaperService.Actions.START.toString()
                            putExtra("homeInterval", homeInterval)
                            putExtra("lockInterval", lockInterval)
                            putExtra("scheduleSeparately", false)
                            putExtra("type", Type.SINGLE.ordinal)
                        }
                        startForegroundService(lockIntent)
                    }
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Error starting foreground service for unlock change", e)
            }
        }
    }

    /**
     * Requests the SCHEDULE_EXACT_ALARM permission if needed on Android 12 (S) and above.
     */
    private fun requestExactAlarmPermissionIfNeeded(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val am = context.getSystemService(ALARM_SERVICE) as AlarmManager
            if (!am.canScheduleExactAlarms()) {
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = "package:${context.packageName}".toUri()
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                requestExactAlarmPermission.launch(intent)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun handleWallpaperScheduling(
        settings: SettingsState,
        scheduler: WallpaperAlarmSchedulerImpl
    ) {
        val wallpaperSettings = settings.wallpaperSettings
        val scheduleSettings = settings.scheduleSettings

        // If changer is not enabled, or the required album name for the selected target is missing, disable changer and return.
        val homeAlbumMissing = wallpaperSettings.setHomeWallpaper && wallpaperSettings.homeAlbumName.isNullOrEmpty()
        val lockAlbumMissing = wallpaperSettings.setLockWallpaper && wallpaperSettings.lockAlbumName.isNullOrEmpty()
        if (!wallpaperSettings.enableChanger || homeAlbumMissing || lockAlbumMissing) {
            if (wallpaperSettings.enableChanger) { // Only dispatch event if it was enabled
                settingsViewModel.onEvent(SettingsEvent.SetChangerToggle(false))
            }
            return
        }

        // Check if alarms are already set to avoid redundant scheduling
        val shouldScheduleAlarm = if (scheduleSettings.scheduleSeparately) {
            !(isPendingIntentSet(Type.HOME.ordinal) && isPendingIntentSet(Type.LOCK.ordinal))
        } else {
            !isPendingIntentSet(Type.SINGLE.ordinal)
        }

        if (shouldScheduleAlarm) {
            val canScheduleExactAlarms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
                alarmManager.canScheduleExactAlarms()
            } else {
                true
            }

            if (canScheduleExactAlarms) {
                scheduleWallpaperAlarm(settings, scheduler)
            } else {
                // Optionally prompt user to grant permission
                requestExactAlarmPermissionIfNeeded(this)
            }
        }
    }

    @RequiresPermission(Manifest.permission.SCHEDULE_EXACT_ALARM)
    private suspend fun scheduleWallpaperAlarm(
        settings: SettingsState,
        scheduler: WallpaperAlarmSchedulerImpl
    ) {
        val scheduleSettings = settings.scheduleSettings
        val wallpaperSettings = settings.wallpaperSettings

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val am = getSystemService(ALARM_SERVICE) as AlarmManager
            if (!am.canScheduleExactAlarms()) {
                return
            }
        }

        scheduler.scheduleWallpaperAlarm(
            WallpaperAlarmItem(
                homeInterval = scheduleSettings.homeInterval,
                lockInterval = scheduleSettings.lockInterval,
                setLock = wallpaperSettings.setLockWallpaper,
                setHome = wallpaperSettings.setHomeWallpaper,
                scheduleSeparately = scheduleSettings.scheduleSeparately,
                changeStartTime = scheduleSettings.changeStartTime,
                startTime = scheduleSettings.startTime,
            ),
            origin = null,
            changeImmediate = true,
            cancelImmediate = true,
            firstLaunch = true,
            homeNextTime = settingsDataStoreImpl.getString(SettingsConstants.HOME_NEXT_SET_TIME),
            lockNextTime = settingsDataStoreImpl.getString(SettingsConstants.LOCK_NEXT_SET_TIME)
        )
        settingsViewModel.onEvent(SettingsEvent.RefreshNextSetTime)
    }

    private fun isPendingIntentSet(requestCode: Int): Boolean {
        val intent = Intent(applicationContext, WallpaperReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            applicationContext,
            requestCode,
            intent,
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_NO_CREATE
        )
        return pendingIntent != null
    }
}
