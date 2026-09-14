package com.janhelmich.deixis

import android.app.Application
import com.janhelmich.deixis.di.AppContainer

class DeixisApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
