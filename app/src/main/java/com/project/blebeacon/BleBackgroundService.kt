// BleBackgroundService.kt
package com.project.blebeacon

import android.app.*
import android.content.Intent
import android.os.IBinder
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import android.app.PendingIntent
import android.graphics.Color
import kotlinx.coroutines.flow.*

class BleBackgroundService : Service() {
    private lateinit var bleManager: BleManager
    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())
    private var scanJob: Job? = null
    private lateinit var deviceId: String
    private val processedDevices = mutableListOf<BluetoothDeviceWrapper>()
    private val deviceHistory = mutableMapOf<String, MutableList<Pair<Long, Int>>>()
    private val lastSeenTime = mutableMapOf<String, Long>()
    private val deviceDisappearanceThreshold = 2000
    private val rssiThreshold = 5

    companion object {
        private const val NOTIFICATION_ID = 123
        private const val CHANNEL_ID = "BleBeaconChannel"
        private const val CHANNEL_NAME = "BLE Beacon Scanner"
    }

    override fun onCreate() {
        super.onCreate()
        bleManager = BleManager(this)
        deviceId = android.provider.Settings.Secure.getString(
            contentResolver,
            android.provider.Settings.Secure.ANDROID_ID
        )
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification("Scanning for BLE devices..."))
        startScanning()
        return START_STICKY
    }

    private fun startScanning() {
        bleManager.startScanning()

        scanJob = serviceScope.launch {
            flow {
                while (currentCoroutineContext().isActive) {
                    emit(Unit)
                    delay(1000)
                }
            }
                .cancellable()
                .conflate()
                .collect {
                    val currentTime = System.currentTimeMillis()
                    val latestDevices = bleManager.scannedDevices.value
                    processDevices(latestDevices, currentTime)

                    // Update notification with current device count
                    updateNotification("Scanning: ${processedDevices.size} devices found")

                    // Create and emit detection
                    if (processedDevices.isNotEmpty()) {
                        val detection = Detection(
                            timestamp = java.text.SimpleDateFormat(
                                "yyyy-MM-dd'T'HH:mm:ss.SSSSSS",
                                java.util.Locale.getDefault()
                            ).format(java.util.Date(currentTime)),
                            devices = processedDevices.toList()
                        )

                        // Emit to shared state
                        BleSharedState.emitDetection(detection)

                        // Send to API if needed
                        sendDetectionToApi(detection)
                    }
                }
        }
    }

    private fun processDevices(devices: List<BluetoothDeviceWrapper>, currentTime: Long) {
        val devicesToRemove = mutableListOf<String>()

        for (device in devices) {
            val history = deviceHistory.getOrPut(device.address) { mutableListOf() }
            history.add(Pair(currentTime, device.rssi))

            val fiveSecondsAgo = currentTime - 2000
            deviceHistory[device.address] = history.filter { it.first >= fiveSecondsAgo }.toMutableList()

            lastSeenTime[device.address] = currentTime

            if (hasDeviceMoved(history)) {
                val existingIndex = processedDevices.indexOfFirst { it.address == device.address }
                if (existingIndex != -1) {
                    processedDevices[existingIndex] = device
                } else {
                    processedDevices.add(device)
                }
            } else {
                devicesToRemove.add(device.address)
            }
        }

        processedDevices.removeAll { it.address in devicesToRemove }
        processedDevices.removeAll { device ->
            val lastSeen = lastSeenTime[device.address] ?: 0L
            currentTime - lastSeen > deviceDisappearanceThreshold
        }
    }

    private fun hasDeviceMoved(history: List<Pair<Long, Int>>): Boolean {
        if (history.size < 1) return false
        val rssiValues = history.map { it.second }
        for (i in 1 until rssiValues.size) {
            if (Math.abs(rssiValues[i] - rssiValues[i-1]) >= rssiThreshold) {
                return true
            }
        }
        return false
    }

    private suspend fun sendDetectionToApi(detection: Detection) {
        try {
            val addresses = detection.devices.map { it.address }
            val rssiValues = detection.devices.map { it.rssi }

            withContext(Dispatchers.IO) {
                RetrofitInstance.apiService.postDetection(
                    DetectionRequest(
                        deviceid = deviceId,
                        timestamp = detection.timestamp,
                        device = detection.devices.size,
                        addresses = addresses,
                        rssi = rssiValues
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "BLE Beacon Scanner Service"
                enableLights(true)
                lightColor = Color.BLUE
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(content: String): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("BLE Beacon Scanner")
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun updateNotification(content: String) {
        val notification = createNotification(content)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scanJob?.cancel()
        serviceScope.cancel()
        bleManager.stopScanning()
        super.onDestroy()
    }
}