package com.anthonyla.paperize.feature.wallpaper.presentation.settings_screen

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.anthonyla.paperize.R
import com.anthonyla.paperize.feature.wallpaper.presentation.settings_screen.SettingsState.ThemeSettings
import com.anthonyla.paperize.feature.wallpaper.presentation.settings_screen.components.AmoledListItem
import com.anthonyla.paperize.feature.wallpaper.presentation.settings_screen.components.AnimationListItem
import com.anthonyla.paperize.feature.wallpaper.presentation.settings_screen.components.ContactListItem
import com.anthonyla.paperize.feature.wallpaper.presentation.settings_screen.components.DarkModeListItem
import com.anthonyla.paperize.feature.wallpaper.presentation.settings_screen.components.DynamicThemingListItem
import com.anthonyla.paperize.feature.wallpaper.presentation.settings_screen.components.ListSectionTitle
import com.anthonyla.paperize.feature.wallpaper.presentation.settings_screen.components.NotificationListItem
import com.anthonyla.paperize.feature.wallpaper.presentation.settings_screen.components.AutoWallpaperChangerProListItem
import com.anthonyla.paperize.feature.wallpaper.presentation.settings_screen.components.PrivacyPolicyListItem
import com.anthonyla.paperize.feature.wallpaper.presentation.settings_screen.components.ResetListItem
import com.anthonyla.paperize.feature.wallpaper.presentation.settings_screen.components.TranslateListItem

private object Links {
    const val TRANSLATE = "https://crowdin.com/project/paperize/invite?h=d8d7a7513d2beb0c96ba9b2a5f85473e2084922"
    const val GITHUB = "https://github.com/Anthonyy232/AutoWallpaperChangerPro"
    const val FDROID = "https://f-droid.org/en/packages/com.anthonyla.paperize/"
    const val IZZY = "https://apt.izzysoft.de/fdroid/index/apk/com.anthonyla.paperize"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    themeSettings: ThemeSettings,
    onBackClick: () -> Unit,
    onDarkModeClick: (Boolean?) -> Unit,
    onAmoledClick: (Boolean) -> Unit,
    onDynamicThemingClick: (Boolean) -> Unit,
    onAnimateClick: (Boolean) -> Unit,
    onPrivacyClick: () -> Unit,
    onResetClick: () -> Unit,
    onContactClick: () -> Unit
) {
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        text = stringResource(id = R.string.settings_screen),
                        style = MaterialTheme.typography.headlineMedium
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.home_screen)
                        )
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { paddingValues ->
        SettingsContent(
            themeSettings = themeSettings,
            onDarkModeClick = onDarkModeClick,
            onAmoledClick = onAmoledClick,
            onDynamicThemingClick = onDynamicThemingClick,
            onAnimateClick = onAnimateClick,
            onPrivacyClick = onPrivacyClick,
            onResetClick = onResetClick,
            onContactClick = onContactClick,
            context = context,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        )
    }
}

@Composable
private fun SettingsContent(
    themeSettings: ThemeSettings,
    onDarkModeClick: (Boolean?) -> Unit,
    onAmoledClick: (Boolean) -> Unit,
    onDynamicThemingClick: (Boolean) -> Unit,
    onAnimateClick: (Boolean) -> Unit,
    onPrivacyClick: () -> Unit,
    onResetClick: () -> Unit,
    onContactClick: () -> Unit,
    context: android.content.Context,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.Top,
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
    ) {
        AppearanceSection(themeSettings, onDarkModeClick, onAmoledClick, onDynamicThemingClick, onAnimateClick)
        AboutSection(context, onPrivacyClick, onContactClick, onResetClick)
    }
}

@Composable
private fun AppearanceSection(
    themeSettings: ThemeSettings,
    onDarkModeClick: (Boolean?) -> Unit,
    onAmoledClick: (Boolean) -> Unit,
    onDynamicThemingClick: (Boolean) -> Unit,
    onAnimateClick: (Boolean) -> Unit
) {
    ListSectionTitle(stringResource(R.string.appearance))
    Spacer(modifier = Modifier.height(16.dp))
    DarkModeListItem(
        darkMode = themeSettings.darkMode,
        onDarkModeClick = onDarkModeClick
    )
    Spacer(modifier = Modifier.height(16.dp))
    if (themeSettings.darkMode == null || themeSettings.darkMode == true) {
        AmoledListItem(
            amoledMode = themeSettings.amoledTheme,
            onAmoledClick = onAmoledClick
        )
        Spacer(modifier = Modifier.height(16.dp))
    }
    DynamicThemingListItem(
        dynamicTheming = themeSettings.dynamicTheming,
        onDynamicThemingClick = onDynamicThemingClick
    )
    Spacer(modifier = Modifier.height(16.dp))
    AnimationListItem(
        animate = themeSettings.animate,
        onAnimateClick = onAnimateClick
    )
    Spacer(modifier = Modifier.height(16.dp))
}

private fun safeStartActivity(context: android.content.Context, intent: Intent, errorMsg: String = "Unable to open") {
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Log.e("SettingsScreen", errorMsg, e)
        Toast.makeText(context, errorMsg, Toast.LENGTH_SHORT).show()
    } catch (e: SecurityException) {
        Log.e("SettingsScreen", errorMsg, e)
        Toast.makeText(context, errorMsg, Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Log.e("SettingsScreen", errorMsg, e)
        Toast.makeText(context, errorMsg, Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun AboutSection(
    context: android.content.Context,
    onPrivacyClick: () -> Unit,
    onContactClick: () -> Unit,
    onResetClick: () -> Unit
) {
    ListSectionTitle(stringResource(R.string.about))
    Spacer(modifier = Modifier.height(16.dp))
    NotificationListItem {
        val intent = Intent().apply {
            action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        }
        safeStartActivity(context, intent, "Unable to open notification settings")
    }
    Spacer(modifier = Modifier.height(16.dp))
    TranslateListItem {
        safeStartActivity(context, Intent(Intent.ACTION_VIEW, Uri.parse(Links.TRANSLATE)), "Unable to open translation page")
    }
    Spacer(modifier = Modifier.height(16.dp))
    PrivacyPolicyListItem(onPrivacyClick)
    Spacer(modifier = Modifier.height(16.dp))
    ContactListItem(onContactClick)
    Spacer(modifier = Modifier.height(16.dp))
    AutoWallpaperChangerProListItem(
        onGitHubClick = { safeStartActivity(context, Intent(Intent.ACTION_VIEW, Uri.parse(Links.GITHUB)), "Unable to open GitHub") },
        onFdroidClick = { safeStartActivity(context, Intent(Intent.ACTION_VIEW, Uri.parse(Links.FDROID)), "Unable to open F-Droid") },
        onIzzyOnDroidClick = { safeStartActivity(context, Intent(Intent.ACTION_VIEW, Uri.parse(Links.IZZY)), "Unable to open IzzyOnDroid") }
    )
    Spacer(modifier = Modifier.height(16.dp))
    ResetListItem(onResetClick)
    Spacer(modifier = Modifier.height(16.dp))
}
