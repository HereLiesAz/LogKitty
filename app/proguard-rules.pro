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
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Dynamic feature entry points are instantiated reflectively by FeatureLoader using the class names
# in core's FeatureModules, so R8 can't see them as used. Keep the classes and their no-arg
# constructors (the implemented interfaces are kept by core's consumer rules).
-keep class com.hereliesaz.logkitty.feature.stats.StatsFeatureImpl { <init>(); }

-keep class com.hereliesaz.logkitty.feature.github.GitHubFeatureImpl { <init>(); }

# The Play Billing Library's bundled consumer rules only keep the *fields* of its generated
# proto-lite message classes (`-keepclassmembers class * extends ...zzgp { <fields>; }`), not the
# classes themselves. Under R8 full mode those message classes are otherwise only reachable via the
# proto runtime's own reflection, so tree-shaking removes them entirely — BillingManager's
# background connection callback (com.android.billingclient.api.BillingClientStateListener) then
# crashes a few seconds after launch with NoClassDefFoundError the first time it touches one.
# (android.r8.strictFullModeForKeepRules=false in gradle.properties suppresses the build-time
# warning R8 would otherwise raise for this exact gap, so it only surfaces at runtime.)
-keep class com.google.android.gms.internal.play_billing.** { *; }
-keep class com.android.billingclient.api.** { *; }

# Same failure mode, different library: com.google.android.play:app-update ships NO consumer
# proguard rules at all (its AAR has no proguard.txt), yet AppUpdateManager's IPC with the Play
# Store app (checkForAppUpdate(), called on every launch from MainActivity.onCreate) relies on
# obfuscated com.google.android.play.core.** classes only reachable through the library's own
# reflection/Binder callback plumbing. Without an explicit keep, R8 strips them the same way it
# stripped Billing's proto-lite classes above.
-keep class com.google.android.play.core.** { *; }
-dontwarn com.google.android.play.core.**
-keep class kotlin.jvm.internal.** { *; }
-keep class androidx.work.** { *; }
-keep class androidx.core.** { *; }
-keep class okhttp3.** { *; }
-keep class kotlinx.coroutines.** { *; }
-keep class kotlinx.serialization.** { *; }
-keep class androidx.lifecycle.** { *; }
-keep class androidx.compose.** { *; }
-keep class com.hereliesaz.** { *; }
-keep class com.dokar3.** { *; }
-keep class dokar3.** { *; }
-keep class io.github.dokar3.** { *; }
-keep class dokar.sheets.** { *; }
