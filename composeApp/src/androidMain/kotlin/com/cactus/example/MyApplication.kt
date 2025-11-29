package com.cactus.example

import android.app.Application
import com.cactus.CactusContextInitializer

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CactusContextInitializer.initialize(this)
    }
}
