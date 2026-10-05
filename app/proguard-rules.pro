# R8 rules for LogKitty.
#
# Keep only what is reached by name at runtime. Everything else is shrunk, optimized and obfuscated;
# libraries (Kotlin, coroutines, AndroidX/Compose, WorkManager, Play services, kotlinx.serialization)
# ship their own consumer rules. Blanket package keeps (kotlin.**, androidx.**, com.google.**, …)
# used to sit here and left Play's DEX optimization / obfuscation / shrinking scores at ~11%.

# Stack traces: keep line numbers, hide the original file names.
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Dynamic feature entry point, instantiated by name in core FeatureLoader (FeatureModules.STATS_IMPL).
# The StatsFeature interface itself is kept by core/consumer-rules.pro.
-keep class com.hereliesaz.logkitty.feature.stats.StatsFeatureImpl { <init>(); }

# Enums parsed from stored names (CodingFont, LogColorScheme, LogLevel via valueOf).
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Exported/imported settings JSON (kotlinx.serialization). The plugin generates the serializer and
# the library ships the generic rules; keep the companion lookup for this class explicitly.
-keepclassmembers class com.hereliesaz.logkitty.utils.ExportedPreferences {
    *** Companion;
}
-keepclasseswithmembers class com.hereliesaz.logkitty.utils.ExportedPreferences {
    kotlinx.serialization.KSerializer serializer(...);
}

# com.google.android.play:app-update ships NO consumer rules, yet AppUpdateManager's IPC with the
# Play Store (checkForAppUpdate(), every launch) reaches com.google.android.play.core.** only through
# the library's own reflection/Binder callbacks. Without this keep R8 strips them.
-keep class com.google.android.play.core.** { *; }
-dontwarn com.google.android.play.core.**

# AzNavRail's overlay sheet host drives WindowManager / accessibility callbacks; the library ships
# no consumer rules, so keep it whole rather than risk a stripped callback.
-keep class com.hereliesaz.aznavrail.** { *; }

# Optional transitive references with no runtime use.
-dontwarn okhttp3.**
-dontwarn okio.**
