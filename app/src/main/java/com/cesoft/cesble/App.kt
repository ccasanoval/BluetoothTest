package com.cesoft.cesble

import android.app.Application
import com.cesoft.cesble.devices.globalModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.dsl.module

val appModule = module {
    single { App() }
}
class App : Application() {

    companion object {
        lateinit var instance: Application
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        startKoin {
            androidContext(this@App)
            modules(appModule)
            modules(globalModule)
        }
    }
}