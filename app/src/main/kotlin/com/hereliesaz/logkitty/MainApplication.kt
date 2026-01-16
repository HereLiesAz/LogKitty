package com.hereliesaz.logkitty

import android.app.Application
import com.hereliesaz.logkitty.ui.MainViewModel

class MainApplication : Application() {

    lateinit var mainViewModel: MainViewModel
        private set

    override fun onCreate() {
        super.onCreate()
        mainViewModel = MainViewModel(this)
    }
}
