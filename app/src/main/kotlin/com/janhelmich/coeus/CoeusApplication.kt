package com.janhelmich.coeus

import android.app.Application
import com.janhelmich.coeus.di.AppContainer

class CoeusApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
