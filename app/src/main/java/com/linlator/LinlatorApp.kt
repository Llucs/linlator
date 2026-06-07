package com.linlator

import android.app.Application
import com.linlator.container.ContainerManager

class LinlatorApp : Application() {

    lateinit var containerManager: ContainerManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        containerManager = ContainerManager(applicationContext.filesDir)
    }

    companion object {
        lateinit var instance: LinlatorApp
            private set
    }
}
