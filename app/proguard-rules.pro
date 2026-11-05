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
-renamesourcefileattribute SourceFile

# AndroidX WorkManager & Room Database reflection
# Room instantiates WorkDatabase_Impl via reflection; R8 strips its constructor unless kept
-keep class * extends androidx.room.RoomDatabase {
    public <init>();
    *;
}
-keep class androidx.work.impl.WorkDatabase_Impl {
    public <init>();
    *;
}
-keep class androidx.work.impl.** { *; }
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class com.cptodix.rimalux.oled.wallpaper.scheduler.WallpaperRotationWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# AndroidX App Startup Initializers (loaded reflectively by InitializationProvider)
-keep class * implements androidx.startup.Initializer {
    public <init>();
}

# Google Play Billing Library
-keep class com.android.billingclient.api.** { *; }

# Rimalux Core Engine and Data Models (preserve enum names, pattern IDs, palette IDs)
-keep class com.cptodix.rimalux.oled.wallpaper.model.** { *; }
-keep class com.cptodix.rimalux.oled.wallpaper.engine.** { *; }

# Kotlin Coroutines
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
