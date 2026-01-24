package com.hereliesaz.logkitty.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.IOException

object LogcatReader {
    fun observe(useRoot: Boolean): Flow<String> = flow {
        val cmd = if (useRoot) listOf("su", "-c", "logcat -v time") else listOf("logcat", "-v", "time")
        
        // Endless loop to auto-restart the process if it dies
        while (currentCoroutineContext().isActive) {
            var process: Process? = null
            try {
                val pb = ProcessBuilder(cmd)
                pb.redirectErrorStream(true) // Merge stderr into stdout
                process = pb.start()

                val reader = BufferedReader(InputStreamReader(process.inputStream))
                
                var line: String? = reader.readLine()
                while (currentCoroutineContext().isActive && line != null) {
                    emit(line)
                    line = reader.readLine()
                }
            } catch (e: IOException) {
                emit("Logcat reader warning: ${e.message}. Retrying...")
                delay(2000) // Don't spam retries if something is permanently broken
            } catch (e: Exception) {
                emit("Logcat reader failed: ${e.message}")
                delay(5000)
            } finally {
                process?.destroy()
            }
            
            // If we are here, the process died. Wait a bit before restarting.
            if (currentCoroutineContext().isActive) {
                delay(1000)
            }
        }
    }.flowOn(Dispatchers.IO)
}
