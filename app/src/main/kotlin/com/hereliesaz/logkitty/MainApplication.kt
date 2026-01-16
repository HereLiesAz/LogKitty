package com.hereliesaz.logkitty

import android.app.Application
import com.hereliesaz.logkitty.ui.MainViewModel
import com.hereliesaz.logkitty.utils.CrashReporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainApplication : Application() {

    lateinit var mainViewModel: MainViewModel
        private set

    override fun onCreate() {
        super.onCreate()

        val crashReporter = CrashReporter(this)
        crashReporter.init()
        CoroutineScope(Dispatchers.IO).launch {
            crashReporter.uploadPendingReports()
        }

        mainViewModel = MainViewModel(this)
    }
}
