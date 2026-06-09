package com.anthonyla.paperize.feature.wallpaper.presentation.home_screen

import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.anthonyla.paperize.core.ScalingConstants
import com.anthonyla.paperize.core.getFolderMetadata
import com.anthonyla.paperize.core.getWallpaperFromFolder
import com.anthonyla.paperize.feature.wallpaper.domain.model.Album
import com.anthonyla.paperize.feature.wallpaper.domain.model.AlbumWithWallpaperAndFolder
import com.anthonyla.paperize.feature.wallpaper.domain.model.Folder
import com.anthonyla.paperize.feature.wallpaper.presentation.add_album_screen.AddAlbumEvent
import com.anthonyla.paperize.feature.wallpaper.presentation.add_album_screen.AddAlbumViewModel
import com.anthonyla.paperize.feature.wallpaper.presentation.album.AlbumsViewModel
import com.anthonyla.paperize.feature.wallpaper.presentation.home_screen.components.HomeTopBar
import com.anthonyla.paperize.feature.wallpaper.presentation.home_screen.components.getTabItems
import com.anthonyla.paperize.feature.wallpaper.presentation.library_screen.LibraryScreen
import com.anthonyla.paperize.feature.wallpaper.presentation.settings_screen.SettingsState.EffectSettings
import com.anthonyla.paperize.feature.wallpaper.presentation.settings_screen.SettingsState.ScheduleSettings
import com.anthonyla.paperize.feature.wallpaper.presentation.settings_screen.SettingsState.ThemeSettings
import com.anthonyla.paperize.feature.wallpaper.presentation.settings_screen.SettingsState.WallpaperSettings
import com.anthonyla.paperize.feature.wallpaper.presentation.wallpaper_screen.WallpaperScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    albums: List<AlbumWithWallpaperAndFolder>,
    homeSelectedAlbum: AlbumWithWallpaperAndFolder?,
    lockSelectedAlbum: AlbumWithWallpaperAndFolder?,
    themeSettings: ThemeSettings,
    wallpaperSettings: WallpaperSettings,
    scheduleSettings: ScheduleSettings,
    effectSettings: EffectSettings,
    onNavigateAddWallpaper: (String) -> Unit,
    onViewAlbum: (String) -> Unit,
    onDarkCheck: (Boolean) -> Unit,
    onDarkenPercentage: (Int, Int) -> Unit,
    onHomeCheckedChange: (Boolean) -> Unit,
    onLockCheckedChange: (Boolean) -> Unit,
    onScalingChange: (ScalingConstants) -> Unit,
    onSelectAlbum: (AlbumWithWallpaperAndFolder, Boolean, Boolean) -> Unit,
    onScheduleSeparatelyChange: (Boolean) -> Unit,
    onHomeTimeChange: (Int) -> Unit,
    onLockTimeChange: (Int) -> Unit,
    onSettingsClick: () -> Unit,
    onDeselect: (Boolean, Boolean) -> Unit,
    onToggleChanger: (Boolean) -> Unit,
    onBlurPercentageChange: (Int, Int) -> Unit,
    onBlurChange: (Boolean) -> Unit,
    onVignettePercentageChange: (Int, Int) -> Unit,
    onVignetteChange: (Boolean) -> Unit,
    onGrayscalePercentageChange: (Int, Int) -> Unit,
    onGrayscaleChange: (Boolean) -> Unit,
    onChangeStartTimeToggle: (Boolean) -> Unit,
    onStartTimeChange: (TimePickerState) -> Unit,
    onShuffleCheck: (Boolean) -> Unit,
    onRefreshChange: (Boolean) -> Unit,
    onSkipLandscapeChange: (Boolean) -> Unit,
    onSkipNonInteractiveChange: (Boolean) -> Unit,
    onChangeOnUnlockChange: (Boolean) -> Unit,
    addAlbumViewModel: AddAlbumViewModel,
    albumsViewModel: AlbumsViewModel
) {
    val tabItems = getTabItems()
    val pagerState = rememberPagerState(0) { tabItems.size }
    var tabIndex by remember( pagerState.currentPage) { mutableIntStateOf(pagerState.currentPage) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    /** Folder picker for directly selecting a folder from the Library tab */
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
        onResult = { uri: Uri? ->
            if (uri != null) {
                scope.launch(Dispatchers.IO) {
                    try {
                        val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        context.contentResolver.takePersistableUriPermission(uri, takeFlags)

                        val directoryUri = uri.toString()
                        val wallpapers = getWallpaperFromFolder(directoryUri, context)
                        if (wallpapers.isEmpty()) {
                            Log.w("HomeScreen", "No wallpapers found in selected folder")
                            return@launch
                        }
                        val metadata = getFolderMetadata(directoryUri, context)
                        val albumName = metadata.filename.ifEmpty { "Album ${System.currentTimeMillis()}" }

                        val folder = Folder(
                            initialAlbumName = albumName,
                            folderName = metadata.filename,
                            folderUri = directoryUri,
                            wallpapers = wallpapers,
                            coverUri = wallpapers.firstOrNull()?.wallpaperUri ?: "",
                            dateModified = metadata.lastModified,
                            order = 0,
                            key = ""
                        )

                        val albumWithWallpaperAndFolder = AlbumWithWallpaperAndFolder(
                            album = Album(
                                initialAlbumName = albumName,
                                displayedAlbumName = albumName,
                                coverUri = folder.coverUri,
                                homeWallpapersInQueue = emptyList(),
                                lockWallpapersInQueue = emptyList(),
                                selected = false
                            ),
                            wallpapers = emptyList(),
                            folders = listOf(folder)
                        )

                        addAlbumViewModel.onEvent(AddAlbumEvent.SaveAlbumDirect(albumWithWallpaperAndFolder))
                    } catch (e: Exception) {
                        Log.e("HomeScreen", "Error creating album from folder", e)
                    }
                }
            }
        }
    )

    LaunchedEffect(tabIndex) {
        pagerState.animateScrollToPage(tabIndex)
    }
    LaunchedEffect(pagerState.currentPage) {
        tabIndex = pagerState.currentPage
    }

    Scaffold (
        topBar = {
            HomeTopBar(
                showSelectionModeAppBar = false,
                selectionCount = 0,
                onSettingsClick = onSettingsClick,
            )
        }
    ) {
        Column(modifier = Modifier.padding(it)) {
            PrimaryTabRow(
                selectedTabIndex = tabIndex,
            ) {
                tabItems.forEachIndexed { index, item ->
                    Tab(
                        selected = (index == tabIndex),
                        onClick = { tabIndex = index },
                        text = { Text(text = item.title) },
                        icon = {
                            Icon(
                                imageVector = if (index == tabIndex) item.filledIcon else item.unfilledIcon,
                                contentDescription = item.title
                            )
                        }
                    )
                }
            }
            HorizontalPager(
                state = pagerState,
                beyondViewportPageCount = 1
            ) { index ->
                when (index.coerceIn(tabItems.indices)) {
                    0 -> WallpaperScreen(
                        albums = albums,
                        homeSelectedAlbum = homeSelectedAlbum,
                        lockSelectedAlbum = lockSelectedAlbum,
                        themeSettings = themeSettings,
                        wallpaperSettings = wallpaperSettings,
                        scheduleSettings = scheduleSettings,
                        effectSettings = effectSettings,
                        onDarkCheck = onDarkCheck,
                        onDarkenPercentage = onDarkenPercentage,
                        onHomeCheckedChange = onHomeCheckedChange,
                        onLockCheckedChange = onLockCheckedChange,
                        onScalingChange = onScalingChange,
                        onScheduleSeparatelyChange = onScheduleSeparatelyChange,
                        onSelectAlbum = onSelectAlbum,
                        onHomeTimeChange = onHomeTimeChange,
                        onLockTimeChange = onLockTimeChange,
                        onDeselect = onDeselect,
                        onToggleChanger = onToggleChanger,
                        onBlurPercentageChange = onBlurPercentageChange,
                        onBlurChange = onBlurChange,
                        onVignettePercentageChange = onVignettePercentageChange,
                        onVignetteChange = onVignetteChange,
                        onGrayscalePercentageChange = onGrayscalePercentageChange,
                        onGrayscaleChange = onGrayscaleChange,
                        onChangeStartTimeToggle = onChangeStartTimeToggle,
                        onStartTimeChange = onStartTimeChange,
                        onShuffleCheck = onShuffleCheck,
                        onRefreshChange = onRefreshChange,
                        onSkipLandscapeChange = onSkipLandscapeChange,
                        onSkipNonInteractiveChange = onSkipNonInteractiveChange,
                        onChangeOnUnlockChange = onChangeOnUnlockChange
                    )
                    else -> LibraryScreen(
                        albums = albums,
                        onAddNewAlbumClick = { folderPickerLauncher.launch(null) },
                        onViewAlbum = onViewAlbum,
                    )
                }
            }
        }
    }
}
