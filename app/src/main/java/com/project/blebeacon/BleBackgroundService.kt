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

    private var isDetectionEnabled = true
    private var samplingInterval = 1L // Default 30 seconds
    private var configurationJob: Job? = null

    companion object {
        private const val NOTIFICATION_ID = 123
        private const val CHANNEL_ID = "BleBeaconChannel"
        private const val CHANNEL_NAME = "BLE Beacon Scanner"

        private val _apIStatusFlow = MutableStateFlow(false)
        val apiStatusFlow: StateFlow<Boolean> = _apIStatusFlow.asStateFlow()

        private val _scanningStateFlow = MutableStateFlow(false)
        val scanningStateFlow: StateFlow<Boolean> = _scanningStateFlow.asStateFlow()

        private val _detectionConfigFlow = MutableStateFlow(DetectionConfig())
        val detectionConfigFlow: StateFlow<DetectionConfig> = _detectionConfigFlow.asStateFlow()
    }

    override fun onCreate() {
        super.onCreate()
        bleManager = BleManager(this)
        deviceId = android.provider.Settings.Secure.getString(
            contentResolver,
            android.provider.Settings.Secure.ANDROID_ID
        )
        createNotificationChannel()
        startConfigurationMonitoring()
    }

    private fun startConfigurationMonitoring() {
        configurationJob = serviceScope.launch {
            // Periodically fetch configuration from your API
            while (isActive) {
                try {
                    val config = RetrofitInstance.apiService.getLocationInfo(deviceId).body()
                    config?.let {
                        _detectionConfigFlow.value = DetectionConfig(
                            isEnabled = it.is_detection_enabled,
                            samplingInterval = it.sampling_interval.toLong()
                        )

                        // Update local variables
                        isDetectionEnabled = it.is_detection_enabled
                        samplingInterval = it.sampling_interval.toLong()

                        // Restart scanning with new configuration if needed
                        if (isDetectionEnabled && _scanningStateFlow.value) {
                            startScanning()
                        } else if (!isDetectionEnabled && _scanningStateFlow.value) {
                            stopScanning()
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                delay(60000) // Check configuration every minute
            }
        }
    }


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP_SCANNING") {
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, createNotification("Scanning for BLE devices..."))
        if (isDetectionEnabled) {
            startScanning()
        }
        return START_NOT_STICKY
    }


    private fun startScanning() {
        if (scanJob?.isActive == true) {
            scanJob?.cancel()
        }

        if (!isDetectionEnabled) {
            return
        }

        bleManager.startScanning()
        _scanningStateFlow.value = true

        scanJob = serviceScope.launch {
            flow {
                while (currentCoroutineContext().isActive && _scanningStateFlow.value) {
                    emit(Unit)
                    delay(samplingInterval * 1000) // Convert to milliseconds
                }
            }
                .cancellable()
                .conflate()
                .collect {
                    val currentTime = System.currentTimeMillis()
                    val latestDevices = bleManager.scannedDevices.value
                    processDevices(latestDevices, currentTime)

                    if (isDetectionEnabled) {
                        val detection = createDetection(currentTime)
                        updateNotification(if (processedDevices.isEmpty())
                            "Scanning: No devices found"
                        else
                            "Scanning: ${processedDevices.size} devices found"
                        )

                        BleSharedState.emitDetection(detection)
                        sendDetectionToApi(detection)
                    }
                }
        }
    }

    private fun stopScanning() {
        _scanningStateFlow.value = false
        scanJob?.cancel()
        bleManager.stopScanning()
        updateNotification("Scanning stopped")
    }

    private fun createDetection(currentTime: Long): Detection {
        return Detection(
            timestamp = java.text.SimpleDateFormat(
                "yyyy-MM-dd'T'HH:mm:ss.SSSSSS",
                java.util.Locale.getDefault()
            ).format(java.util.Date(currentTime)),
            devices = processedDevices.toList()
        )
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
            withContext(Dispatchers.IO) {
                val request = if (detection.devices.isEmpty()) {
                    DetectionRequest(
                        deviceid = deviceId,
                        timestamp = detection.timestamp,
                        device = 0,
                        addresses = listOf("null"),  // Explicitly send "null" as a string
                        rssi = listOf(-999)           // Use a placeholder value if RSSI is needed as an integer
                    )
                } else {
                    DetectionRequest(
                        deviceid = deviceId,
                        timestamp = detection.timestamp,
                        device = detection.devices.size,
                        addresses = detection.devices.map { it.address },
                        rssi = detection.devices.map { it.rssi }
                    )
                }

                val response = RetrofitInstance.apiService.postDetection(request)
                _apIStatusFlow.emit(response.isSuccessful)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            _apIStatusFlow.emit(false)
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
        _scanningStateFlow.value = false
        scanJob?.cancel()
        configurationJob?.cancel()
        serviceScope.cancel()
        bleManager.stopScanning()
        super.onDestroy()
    }
}