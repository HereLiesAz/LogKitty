package com.hereliesaz.logkitty.utils

import android.content.Context
import android.os.Build
import android.util.Log
import com.hereliesaz.logkitty.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * [CrashReporter] is a custom global exception handler.
 *
 * **Purpose:**
 * Since LogKitty is often used to debug *other* apps, we need to ensure it is extremely stable itself.
 * If it does crash, we want to know why. This class captures unhandled exceptions, saves them to disk,
 * and attempts to upload them to GitHub Issues on the next launch.
 */
class CrashReporter(private val context: Context) : Thread.UncaughtExceptionHandler {

    // Keep a reference to the system's default handler (usually the one that crashes the app).
    private val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

    /**
     * Installs this reporter as the default UncaughtExceptionHandler.
     */
    fun init() {
        Thread.setDefaultUncaughtExceptionHandler(this)
    }

    /**
     * The callback invoked when a thread crashes.
     */
    override fun uncaughtException(t: Thread, e: Throwable) {
        try {
            // Attempt to save the crash to internal storage before the process dies.
            saveCrashReport(e)
        } catch (ex: Exception) {
            Log.e("CrashReporter", "Failed to save crash report", ex)
        } finally {
            // Pass control back to the system handler so the app crashes "normally" (process kill).
            defaultHandler?.uncaughtException(t, e)
        }
    }

    /**
     * Serializes the exception stack trace and device info into a JSON file.
     */
    private fun saveCrashReport(t: Throwable) {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val filename = "crash_$timestamp.txt"

        // Convert stack trace to string
        val sw = StringWriter()
        t.printStackTrace(PrintWriter(sw))
        val stackTrace = sw.toString()

        val report = CrashReport(
            timestamp = timestamp,
            exceptionType = t.javaClass.simpleName,
            message = t.message ?: "No message",
            stackTrace = stackTrace,
            androidVersion = Build.VERSION.RELEASE,
            sdkVersion = Build.VERSION.SDK_INT,
            deviceManufacturer = Build.MANUFACTURER,
            deviceModel = Build.MODEL
        )

        // Write to private file storage
        val json = Json.encodeToString(report)
        context.openFileOutput(filename, Context.MODE_PRIVATE).use {
            it.write(json.toByteArray())
        }
    }

    /**
     * Scans for pending crash report files and attempts to upload them to GitHub.
     * Should be called from a background coroutine on app startup.
     */
    suspend fun uploadPendingReports() = withContext(Dispatchers.IO) {
        val crashDir = context.filesDir
        val crashFiles = crashDir.listFiles { _, name -> name.startsWith("crash_") && name.endsWith(".txt") } ?: return@withContext

        if (crashFiles.isEmpty()) return@withContext

        // Requires a GH_TOKEN injected at build time via BuildConfig.
        val token = BuildConfig.GH_TOKEN
        if (token.isEmpty()) {
            Log.w("CrashReporter", "GH_TOKEN is empty. Cannot upload crash reports.")
            return@withContext
        }

        val client = OkHttpClient()
        val jsonMediaType = "application/json; charset=utf-8".toMediaType()

        for (file in crashFiles) {
            try {
                val jsonContent = file.readText()
                val report = Json.decodeFromString<CrashReport>(jsonContent)

                // Format the GitHub Issue
                val issueTitle = "Crash: ${report.exceptionType} - ${report.timestamp}"
                val issueBody = """
                    **Crash Details**
                    - **Message**: ${report.message}
                    - **Timestamp**: ${report.timestamp}
                    - **Device**: ${report.deviceManufacturer} ${report.deviceModel}
                    - **Android**: ${report.androidVersion} (SDK ${report.sdkVersion})

                    **Stack Trace**:
                    ```
                    ${report.stackTrace}
                    ```
                """.trimIndent()

                val issuePayload = IssuePayload(title = issueTitle, body = issueBody)
                val requestBody = Json.encodeToString(issuePayload).toRequestBody(jsonMediaType)

                // POST to GitHub API
                val request = Request.Builder()
                    .url("https://api.github.com/repos/${BuildConfig.REPO_OWNER}/${BuildConfig.REPO_NAME}/issues")
                    .addHeader("Authorization", "token $token")
                    .addHeader("Accept", "application/vnd.github.v3+json")
                    .post(requestBody)
                    .build()

                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    // Delete the file only if upload succeeded
                    file.delete()
                    Log.i("CrashReporter", "Crash report uploaded successfully: ${file.name}")
                } else {
                    Log.e("CrashReporter", "Failed to upload crash report: ${response.code} ${response.message}")
                }
                response.close()
            } catch (e: Exception) {
                Log.e("CrashReporter", "Error uploading report ${file.name}", e)
            }
        }
    }

    @Serializable
    data class CrashReport(
        val timestamp: String,
        val exceptionType: String,
        val message: String,
        val stackTrace: String,
        val androidVersion: String,
        val sdkVersion: Int,
        val deviceManufacturer: String,
        val deviceModel: String
    )

    @Serializable
    data class IssuePayload(
        val title: String,
        val body: String
    )
}
