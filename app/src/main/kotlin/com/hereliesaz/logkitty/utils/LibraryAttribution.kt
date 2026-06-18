package com.hereliesaz.logkitty.utils

/**
 * Maps a Linux thread name (`comm`, max 15 chars) to the library or subsystem it most likely
 * belongs to.
 *
 * You cannot attribute CPU to a specific library *inside another app's process* from the outside —
 * that needs in-process profiling hooks. But almost every library names its worker threads, so a
 * per-thread CPU table effectively shows which library is doing the work. This is the realistic,
 * honest version of "broken down by library": an informed guess from the thread name, good enough
 * to point a developer at the culprit.
 */
object LibraryAttribution {

    // Ordered: more specific matches first. Each entry is (substring-to-match, friendly-label).
    // Matching is case-insensitive and substring-based because `comm` is truncated to 15 chars.
    private val RULES: List<Pair<String, String>> = listOf(
        "okhttp" to "OkHttp",
        "okio" to "Okio",
        "firebase" to "Firebase",
        "firestore" to "Firebase",
        "crashlytics" to "Crashlytics",
        "grpc" to "gRPC",
        "glide" to "Glide",
        "coil" to "Coil",
        "picasso" to "Picasso",
        "exoplayer" to "ExoPlayer / Media3",
        "exo" to "ExoPlayer / Media3",
        "rxcomputation" to "RxJava",
        "rxcachedthread" to "RxJava",
        "rxnewthread" to "RxJava",
        "rxio" to "RxJava",
        "rxschedule" to "RxJava",
        "defaultdispatch" to "Coroutines",
        "kotlinx.coroutin" to "Coroutines",
        "renderthread" to "HWUI / RenderThread",
        "hwuitask" to "HWUI",
        "rendereng" to "RenderEngine",
        "glthread" to "OpenGL",
        "crrenderermain" to "WebView / Chromium",
        "chromium" to "WebView / Chromium",
        "chrome" to "WebView / Chromium",
        "cronet" to "Cronet",
        "googleapihandl" to "Play Services",
        "gmsdynamite" to "Play Services",
        "gms" to "Play Services",
        "finalizerdaemon" to "ART GC",
        "referencequeue" to "ART GC",
        "heaptaskdaemon" to "ART GC",
        "finalizerwatch" to "ART GC",
        "hwbinder" to "HW Binder IPC",
        "binder:" to "Binder IPC",
        "jit thread pool" to "ART JIT",
        "profile saver" to "ART Profile",
        "queued-work-loo" to "SharedPreferences",
        "asynctask" to "AsyncTask",
        "pool-" to "Thread pool",
        "workmanager" to "WorkManager",
        "camerax" to "CameraX",
        "sqlite" to "SQLite / Room",
        "room" to "Room",
        "realm" to "Realm",
        "netty" to "Netty",
        "unity" to "Unity",
        "mono" to "Mono / Unity",
        "flutter" to "Flutter",
        "dartworker" to "Flutter / Dart",
        "hermes" to "React Native (Hermes)",
        "mqt_" to "React Native",
        "reactnative" to "React Native",
    )

    /**
     * Returns the friendly library/subsystem name for [threadName], or `null` if it looks like an
     * app's own thread (e.g. the main thread or a custom-named worker).
     */
    fun forThread(threadName: String): String? {
        val name = threadName.trim()
        if (name.isEmpty()) return null
        if (name.equals("main", ignoreCase = true)) return "App main thread"
        val lower = name.lowercase()
        for ((needle, label) in RULES) {
            if (lower.contains(needle)) return label
        }
        return null
    }
}
