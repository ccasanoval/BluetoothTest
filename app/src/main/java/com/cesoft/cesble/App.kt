package com.cesoft.cesble

import android.app.Application
import com.cesoft.cesble.device.bluetoothDIModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.dsl.module

val appDIModule = module {
    single { App() }
    //single { androidContext() }
}
class App : Application() {

    companion object {
        //lateinit var instance: Application
    }

    override fun onCreate() {
        super.onCreate()
        //instance = this
        startKoin {
            androidContext(this@App)
            modules(appDIModule)
            modules(bluetoothDIModule)
        }
    }
}