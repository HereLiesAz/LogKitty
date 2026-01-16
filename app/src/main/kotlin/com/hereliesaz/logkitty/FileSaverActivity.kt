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
                    // Use filtered logs if context mode is on, or raw logs?
                    // Usually "Save" saves what you see, or everything?
                    // Let's save what is visible (filtered) to be consistent with WYSIWYG,
                    // or maybe provide an option? For now, let's access the raw systemLog from stateDelegate
                    // because MainViewModel doesn't expose systemLog directly anymore in the previous read?
                    // Ah, in the read of MainViewModel above, 'val systemLog' was NOT present!
                    // It only has 'val filteredSystemLog'.
                    // But 'stateDelegate' is public.
                    val logs = app.mainViewModel.stateDelegate.systemLog.value.joinToString("\n")

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
        // Explicitly specify the input type for launch to help type inference if needed,
        // though typically it should be inferred. The error was 'Cannot infer type for type parameter R'.
        // ActivityResultContracts.CreateDocument takes a String (initial name) and returns a Uri?.
        createDocumentLauncher.launch(fileName)
    }
}
