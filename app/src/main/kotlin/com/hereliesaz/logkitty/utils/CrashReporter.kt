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

class CrashReporter(private val context: Context) : Thread.UncaughtExceptionHandler {

    private val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

    fun init() {
        Thread.setDefaultUncaughtExceptionHandler(this)
    }

    override fun uncaughtException(t: Thread, e: Throwable) {
        try {
            saveCrashReport(e)
        } catch (ex: Exception) {
            Log.e("CrashReporter", "Failed to save crash report", ex)
        } finally {
            defaultHandler?.uncaughtException(t, e)
        }
    }

    private fun saveCrashReport(t: Throwable) {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val filename = "crash_$timestamp.txt"
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

        val json = Json.encodeToString(report)
        context.openFileOutput(filename, Context.MODE_PRIVATE).use {
            it.write(json.toByteArray())
        }
    }

    suspend fun uploadPendingReports() = withContext(Dispatchers.IO) {
        val crashDir = context.filesDir
        val crashFiles = crashDir.listFiles { _, name -> name.startsWith("crash_") && name.endsWith(".txt") } ?: return@withContext

        if (crashFiles.isEmpty()) return@withContext

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

                val request = Request.Builder()
                    .url("https://api.github.com/repos/${BuildConfig.REPO_OWNER}/${BuildConfig.REPO_NAME}/issues")
                    .addHeader("Authorization", "token $token")
                    .addHeader("Accept", "application/vnd.github.v3+json")
                    .post(requestBody)
                    .build()

                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
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
