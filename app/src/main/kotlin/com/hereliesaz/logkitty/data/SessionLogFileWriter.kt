package com.hereliesaz.logkitty.data

import android.content.Context
import android.util.Log
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object SessionLogFileWriter {
    private const val TAG = "SessionLogFileWriter"
    private const val LOG_DIR_NAME = "logs"
    private data class SessionData(val writer: BufferedWriter, val file: File)
    private val openSessions = mutableMapOf<String, SessionData>()
    
    private fun getLogsDir(context: Context): File {
        val dir = File(context.filesDir, LOG_DIR_NAME)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    private fun getFileForSession(context: Context, packageName: String): File {
        val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
        val fileName = "${packageName}_${timestamp}.log"
        return File(getLogsDir(context), fileName)
    }

    suspend fun openSession(context: Context, packageName: String): File? = withContext(Dispatchers.IO) {
        if (openSessions.containsKey(packageName)) return@withContext null // Already open
        
        try {
            val file = getFileForSession(context, packageName)
            val writer = BufferedWriter(FileWriter(file, true))
            openSessions[packageName] = SessionData(writer, file)
            file
        } catch (e: IOException) {
            Log.e(TAG, "Failed to open session log file for $packageName", e)
            null
        }
    }

    suspend fun appendLog(packageName: String, logLine: String) = withContext(Dispatchers.IO) {
        val session = openSessions[packageName] ?: return@withContext
        try {
            session.writer.write(logLine)
            session.writer.newLine()
            session.writer.flush()
        } catch (e: IOException) {
            Log.e(TAG, "Failed to write to session log file for $packageName", e)
        }
    }

    suspend fun closeSession(packageName: String): File? = withContext(Dispatchers.IO) {
        val session = openSessions.remove(packageName) ?: return@withContext null
        try {
            session.writer.close()
            session.file
        } catch (e: IOException) {
            Log.e(TAG, "Error closing session log file for $packageName", e)
            null
        }
    }
    
    fun listSavedSessions(context: Context): List<File> {
        val dir = getLogsDir(context)
        return dir.listFiles()?.filter { it.isFile && it.name.endsWith(".log") }?.sortedByDescending { it.lastModified() } ?: emptyList()
    }
}
