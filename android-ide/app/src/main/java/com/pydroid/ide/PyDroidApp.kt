package com.pydroid.ide

import android.app.Application

/** Доступ к Context из любого места (BotRuntime, JNI-обёртки). */
class PyDroidApp : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: PyDroidApp
            private set
    }
}
