package com.hereliesaz.logkitty.data

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.hereliesaz.logkitty.utils.UserPreferences
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

class LogFileCleanupWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting LogFileCleanupWorker")
        val userPreferences = UserPreferences(applicationContext)
        val autoDeleteDays = userPreferences.autoDeleteDurationDays.first()
        var deletedCount = 0

        if (autoDeleteDays > 0) {
            val cutoffTimeMs = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(autoDeleteDays.toLong())
            val savedSessions = SessionLogFileWriter.listSavedSessions(applicationContext)
            for (file in savedSessions) {
                if (file.lastModified() < cutoffTimeMs) {
                    if (file.delete()) {
                        deletedCount++
                        Log.d(TAG, "Deleted old log file: ${file.name}")
                    } else {
                        Log.w(TAG, "Failed to delete old log file: ${file.name}")
                    }
                }
            }
        }

        val maxTotalSizeMb = userPreferences.maxTotalLogSizeMegabytes.first()
        val maxTotalSizeBytes = maxTotalSizeMb * 1024L * 1024L

        if (maxTotalSizeBytes > 0) {
            val remainingFiles = SessionLogFileWriter.listSavedSessions(applicationContext).toMutableList()
            remainingFiles.sortBy { it.lastModified() }
            
            var totalSize = remainingFiles.sumOf { it.length() }
            val iter = remainingFiles.iterator()
            while (iter.hasNext() && totalSize > maxTotalSizeBytes) {
                val file = iter.next()
                val size = file.length()
                if (file.delete()) {
                    totalSize -= size
                    deletedCount++
                    Log.d(TAG, "Deleted log file due to size cap: ${file.name}")
                }
            }
        }

        Log.d(TAG, "LogFileCleanupWorker finished. Deleted $deletedCount files.")
        return Result.success()
    }

    companion object {
        private const val TAG = "LogFileCleanupWorker"
    }
}
