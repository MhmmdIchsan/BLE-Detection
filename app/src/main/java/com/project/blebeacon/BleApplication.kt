package com.project.blebeacon

import android.app.Application
import android.content.Intent
import android.os.Build

class BleApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Start service on application create
        val serviceIntent = Intent(this, BleBackgroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }
}