package com.mallucupid.app

import android.app.Application
import android.content.Context

class MalluCupidApp : Application() {
    companion object {
        lateinit var appContext: Context
            private set
    }

    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext
    }
}
