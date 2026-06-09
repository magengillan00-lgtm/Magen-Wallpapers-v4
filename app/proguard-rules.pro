# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile
-keepattributes Signature
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-keep class kotlin.coroutines.Continuation
-keep class androidx.datastore.*.** {*;}

-keepclassmembers class com.anthonyla.paperize.feature.wallpaper.domain.model.Album {
 !transient <fields>;
}
-keepclassmembers class com.anthonyla.paperize.feature.wallpaper.domain.model.AlbumWithWallpaperAndFolder {
 !transient <fields>;
}
-keepclassmembers class com.anthonyla.paperize.feature.wallpaper.domain.model.Wallpaper {
 !transient <fields>;
}
-keepclassmembers class com.anthonyla.paperize.feature.wallpaper.domain.model.Folder {
 !transient <fields>;
}

# Keep wallpaper services and their action enums
-keep class com.anthonyla.paperize.feature.wallpaper.wallpaper_service.HomeWallpaperService { *; }
-keep class com.anthonyla.paperize.feature.wallpaper.wallpaper_service.HomeWallpaperService$Actions { *; }
-keep class com.anthonyla.paperize.feature.wallpaper.wallpaper_service.LockWallpaperService { *; }
-keep class com.anthonyla.paperize.feature.wallpaper.wallpaper_service.LockWallpaperService$Actions { *; }

# Keep WallpaperAction classes
-keep class com.anthonyla.paperize.feature.wallpaper.wallpaper_alarmmanager.WallpaperAction { *; }
-keep class com.anthonyla.paperize.feature.wallpaper.wallpaper_alarmmanager.WallpaperAction$* { *; }

# Keep Type enum
-keep class com.anthonyla.paperize.core.Type { *; }

# ============================================================
# CRITICAL: Kotlin Serialization rules (Navigation routes use @Serializable)
# Without these, R8 strips the generated serializers and the app crashes
# when navigating to any screen.
# ============================================================
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.anthonyla.paperize.**$$serializer { *; }
-keepclassmembers class com.anthonyla.paperize.** {
    *** Companion;
}
-keepclasseswithmembers class com.anthonyla.paperize.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep all @Serializable classes and their companions
-keep @kotlinx.serialization.Serializable class * { *; }
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <methods>;
}

# ============================================================
# Hilt / Dagger rules
# ============================================================
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class dagger.** { *; }
-keep class com.anthonyla.paperize.App { *; }
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * { *; }
-keep @dagger.hilt.android.AndroidEntryPoint class * { *; }
-keepclassmembers class * {
    @javax.inject.Inject <init>(...);
}
-keepclassmembers class * {
    @dagger.hilt.android.qualifiers.ApplicationContext <fields>;
}

-dontwarn dagger.hilt.**
-dontwarn javax.inject.**

# ============================================================
# Compose rules
# ============================================================
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# Keep Composable functions
-keepclassmembers class * {
    @androidx.compose.runtime.Composable <methods>;
}

# ============================================================
# Navigation Compose rules
# ============================================================
-keep class androidx.navigation.** { *; }
-keepclassmembers class androidx.navigation.** { *; }

# ============================================================
# BroadcastReceiver rules
# ============================================================
-keep class com.anthonyla.paperize.feature.wallpaper.wallpaper_alarmmanager.UnlockReceiver { *; }
-keep class com.anthonyla.paperize.feature.wallpaper.wallpaper_alarmmanager.WallpaperBootAndChangeReceiver { *; }
-keep class com.anthonyla.paperize.feature.wallpaper.wallpaper_alarmmanager.WallpaperReceiver { *; }
-keep class com.anthonyla.paperize.feature.wallpaper.wallpaper_tile.ChangeWallpaperTileService { *; }
-keep class com.anthonyla.paperize.feature.wallpaper.app_shortcut.BroadcastActivity { *; }

# ============================================================
# Room database rules
# ============================================================
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-dontwarn androidx.room.**

# ============================================================
# DataStore rules
# ============================================================
-keep class * extends com.anthonyla.paperize.data.settings.SettingsDataStore { *; }
-keep class com.anthonyla.paperize.data.settings.SettingsDataStoreImpl { *; }

# ============================================================
# ViewModel rules
# ============================================================
-keep class * extends androidx.lifecycle.ViewModel { *; }
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}

# ============================================================
# Generic safe guards - keep all app classes
# This prevents R8 from stripping any app classes that might be
# accessed via reflection or serialization
# ============================================================
-keep class com.anthonyla.paperize.core.** { *; }
-keep class com.anthonyla.paperize.data.** { *; }
-keep class com.anthonyla.paperize.di.** { *; }
-keep class com.anthonyla.paperize.feature.wallpaper.domain.** { *; }
-keep class com.anthonyla.paperize.feature.wallpaper.presentation.** { *; }
-keep class com.anthonyla.paperize.feature.wallpaper.util.** { *; }
-keep class com.anthonyla.paperize.feature.wallpaper.app_shortcut.** { *; }
-keep class com.anthonyla.paperize.feature.wallpaper.glance_widget.** { *; }
-keep class com.anthonyla.paperize.feature.wallpaper.tasker_shortcut.** { *; }

# Keep ScalingConstants enum
-keep class com.anthonyla.paperize.core.ScalingConstants { *; }
