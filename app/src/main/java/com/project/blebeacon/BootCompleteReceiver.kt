package com.project.blebeacon

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log

class BootCompletedReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "BootCompletedReceiver"
        private const val LAUNCH_DELAY = 10000L // 10 seconds delay
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Boot completed receiver triggered with action: ${intent.action}")

        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON" -> {
                Log.d(TAG, "Starting service and activity with delay")

                // Start service immediately
                val serviceIntent = Intent(context, BleBackgroundService::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }

                // Delay the activity launch
                Handler(Looper.getMainLooper()).postDelayed({
                    try {
                        val launchIntent = Intent(context, MainActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                            putExtra("AUTO_START_SCANNING", true)
                        }
                        context.startActivity(launchIntent)
                        Log.d(TAG, "Activity launched successfully")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error launching activity", e)
                    }
                }, LAUNCH_DELAY)
            }
        }
    }
}