package com.hereliesaz.logkitty

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FileSaverActivity : ComponentActivity() {

    private val createDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null) {
            try {
                val app = application as MainApplication
                val logs = app.mainViewModel.systemLog.value.joinToString("\n")

                contentResolver.openOutputStream(uri)?.use { output ->
                    output.write(logs.toByteArray())
                }
                Toast.makeText(this, "Logs saved successfully", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this, "Failed to save logs: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
        createDocumentLauncher.launch("logkitty_$timestamp.txt")
    }
}
