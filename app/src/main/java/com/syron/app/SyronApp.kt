package com.syron.app

import android.app.Application
import com.syron.app.di.AppContainer

class SyronApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
