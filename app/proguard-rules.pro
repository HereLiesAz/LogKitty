# Preserve critical metadata for reflection, serialization, coroutines, and stack traces
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,SourceFile,LineNumberTable

# Preserve line numbers for stack traces
-keepattributes SourceFile,LineNumberTable

# Keep all project application code and feature modules
-keep class com.hereliesaz.** { *; }
-keepclassmembers class com.hereliesaz.** { *; }
-keep interface com.hereliesaz.** { *; }

# Dynamic feature entry points instantiated reflectively by FeatureLoader
-keep class com.hereliesaz.logkitty.feature.stats.StatsFeatureImpl { <init>(); }

-keep class com.hereliesaz.logkitty.feature.github.GitHubFeatureImpl { <init>(); }

# Google Play Services, Play Core, App Update, Play Feature Delivery, Fonts & Billing
-keep class com.google.android.gms.** { *; }
-keepclassmembers class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**

-keep class com.google.android.play.** { *; }
-keepclassmembers class com.google.android.play.** { *; }
-dontwarn com.google.android.play.**

-keep class com.android.billingclient.api.** { *; }
-keepclassmembers class com.android.billingclient.api.** { *; }
-dontwarn com.android.billingclient.api.**

# Kotlin Language, Coroutines, and Serialization
-keep class kotlin.** { *; }
-keep class kotlin.jvm.internal.** { *; }
-keep class kotlinx.coroutines.** { *; }
-keep class kotlinx.serialization.** { *; }
-keepclassmembers class * {
    @kotlinx.serialization.Serializable *;
}

# AndroidX Architecture Components, Compose, WorkManager, and Navigation
-keep class androidx.** { *; }
-dontwarn androidx.**

# Networking (OkHttp / Okio)
-keep class okhttp3.** { *; }
-keep class okio.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

# Same failure mode, different library: com.google.android.play:app-update ships NO consumer
# proguard rules at all (its AAR has no proguard.txt), yet AppUpdateManager's IPC with the Play
# Store app (checkForAppUpdate(), called on every launch from MainActivity.onCreate) relies on
# obfuscated com.google.android.play.core.** classes only reachable through the library's own
# reflection/Binder callback plumbing. Without an explicit keep, R8 strips them the same way it
# stripped Billing's proto-lite classes above.
-keep class com.google.android.play.core.** { *; }
-dontwarn com.google.android.play.core.**
-keep class kotlin.** { *; }
-keep class kotlinx.** { *; }
-keep class androidx.** { *; }
-keep class com.hereliesaz.** { *; }
-keep class io.github.dokar3.** { *; }
-keep class okhttp3.** { *; }
-keep class okio.** { *; }
-keep class com.google.** { *; }
-keep class java.** { *; }
-keep class javax.** { *; }
-keep class org.** { *; }
-keep class sun.** { *; }
-keep class com.sun.** { *; }
# UI Overlay & Sheet Libraries (Dokar3 & AzNavRail)
-keep class com.dokar3.** { *; }
-keep class dokar3.** { *; }
-keep class io.github.dokar3.** { *; }
-keep class dokar.sheets.** { *; }
-keep class com.hereliesaz.aznavrail.** { *; }

# Keep enum methods for reflection and serialization lookup
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
