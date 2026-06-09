package com.anthonyla.paperize.feature.wallpaper.data.repository

import com.anthonyla.paperize.feature.wallpaper.data.data_source.AlbumDao
import com.anthonyla.paperize.feature.wallpaper.domain.model.Album
import com.anthonyla.paperize.feature.wallpaper.domain.model.AlbumWithWallpaperAndFolder
import com.anthonyla.paperize.feature.wallpaper.domain.model.Folder
import com.anthonyla.paperize.feature.wallpaper.domain.model.Wallpaper
import com.anthonyla.paperize.feature.wallpaper.domain.repository.AlbumRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.Objects

class AlbumRepositoryImpl(
    private val dao: AlbumDao,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
): AlbumRepository {
    override fun getAlbumsWithWallpaperAndFolder(): Flow<List<AlbumWithWallpaperAndFolder>> {
        return dao.getAlbumsWithWallpaperAndFolder().map { albumWithWallpaperAndFolderList ->
            albumWithWallpaperAndFolderList.map {
                it.copy(
                    wallpapers = it.sortedWallpapers,
                    folders = it.sortedFolders,
                )
            }
        }
    }

    override fun getSelectedAlbums(): Flow<List<AlbumWithWallpaperAndFolder>> {
        return dao.getSelectedAlbums().map { albumWithWallpaperAndFolderList ->
            albumWithWallpaperAndFolderList.map {
                it.copy(
                    wallpapers = it.sortedWallpapers,
                    folders = it.sortedFolders,
                )
            }
        }
    }

    override suspend fun upsertAlbumWithWallpaperAndFolder(albumWithWallpaperAndFolder: AlbumWithWallpaperAndFolder) {
        withContext(dispatcher) {
            val albumName = albumWithWallpaperAndFolder.album.initialAlbumName

            // Use collision-resistant String keys: "albumName|uri"
            // This avoids Integer hashCode collisions that could cause data loss
            val safeWallpapers = albumWithWallpaperAndFolder.wallpapers.map { w ->
                w.copy(key = "$albumName|${w.wallpaperUri}")
            }
            val safeFolders = albumWithWallpaperAndFolder.folders.map { f ->
                f.copy(key = "$albumName|${f.folderUri}")
            }

            // Delete existing wallpapers and folders for THIS album only before re-inserting
            dao.cascadeDeleteWallpaper(albumName)
            dao.cascadeDeleteFolder(albumName)

            dao.upsertAlbum(albumWithWallpaperAndFolder.album)
            dao.upsertWallpaperList(safeWallpapers)
            dao.upsertFolderList(safeFolders)
        }
    }

    override suspend fun updateAlbumSelection(albumName: String, selected: Boolean) {
        withContext(dispatcher) {
            dao.updateAlbumSelection(albumName, selected)
        }
    }

    override suspend fun deselectAllAlbums() {
        withContext(dispatcher) {
            dao.deselectAllAlbums()
        }
    }

    override suspend fun upsertAlbum(album: Album) {
        withContext(dispatcher) {
            dao.upsertAlbum(album)
        }
    }

    override suspend fun upsertWallpaper(wallpaper: Wallpaper) {
        withContext(dispatcher) {
            dao.upsertWallpaper(wallpaper)
        }
    }

    override suspend fun upsertFolder(folder: Folder) {
        withContext(dispatcher) {
            dao.upsertFolder(folder)
        }
    }

    override suspend fun upsertFolderList(folders: List<Folder>) {
        withContext(dispatcher) {
            dao.upsertFolderList(folders)
        }
    }

    override suspend fun upsertWallpaperList(wallpapers: List<Wallpaper>) {
        withContext(dispatcher) {
            dao.upsertWallpaperList(wallpapers)
        }
    }

    override suspend fun deleteAlbum(album: Album) {
        withContext(dispatcher) {
            dao.deleteAlbum(album)
        }
    }

    override suspend fun deleteWallpaper(wallpaper: Wallpaper) {
        withContext(dispatcher) {
            dao.deleteWallpaper(wallpaper)
        }
    }

    override suspend fun deleteFolder(folder: Folder) {
        withContext(dispatcher) {
            dao.deleteFolder(folder)
        }
    }

    override suspend fun updateAlbum(album: Album) {
        withContext(dispatcher) {
            dao.updateAlbum(album)
        }
    }

    override suspend fun updateFolder(folder: Folder) {
        withContext(dispatcher) {
            dao.updateFolder(folder)
        }
    }

    override suspend fun updateWallpaper(wallpaper: Wallpaper) {
        withContext(dispatcher) {
            dao.updateWallpaper(wallpaper)
        }
    }

    override suspend fun cascadeDeleteAlbum(album: Album) {
        withContext(dispatcher) {
            dao.deleteAlbum(album)
            dao.cascadeDeleteWallpaper(album.initialAlbumName)
            dao.cascadeDeleteFolder(album.initialAlbumName)
        }
    }

    override suspend fun cascadeDeleteFolder(initialAlbumName: String) {
        withContext(dispatcher) {
            dao.cascadeDeleteFolder(initialAlbumName)
        }
    }

    override suspend fun cascadeDeleteWallpaper(initialAlbumName: String) {
        withContext(dispatcher) {
            dao.cascadeDeleteWallpaper(initialAlbumName)
        }
    }

    override suspend fun deleteFolderList(folders: List<Folder>) {
        withContext(dispatcher) {
            dao.deleteFolderList(folders)
        }
    }

    override suspend fun deleteWallpaperList(wallpapers: List<Wallpaper>) {
        withContext(dispatcher) {
            dao.deleteWallpaperList(wallpapers)
        }
    }

    override suspend fun deleteAllData() {
        withContext(dispatcher) {
            dao.deleteAllData()
        }
    }
}