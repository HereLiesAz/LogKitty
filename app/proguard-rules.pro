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
