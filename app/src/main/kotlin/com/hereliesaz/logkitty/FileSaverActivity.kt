package com.hereliesaz.logkitty

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FileSaverActivity : ComponentActivity() {

    private val createDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null) {
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val app = application as MainApplication
                    
                    // We now capture the FILTERED logs (what the user sees)
                    // rather than the raw firehose.
                    val logs = app.mainViewModel.filteredSystemLog.value.joinToString("\n")

                    contentResolver.openOutputStream(uri)?.use { output ->
                        output.write(logs.toByteArray())
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@FileSaverActivity, "Logs saved successfully", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@FileSaverActivity, "Failed to save logs: ${e.message}", Toast.LENGTH_LONG).show()
                        finish()
                    }
                }
            }
        } else {
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
        val fileName = "logkitty_$timestamp.txt"
        createDocumentLauncher.launch(fileName)
    }
}
