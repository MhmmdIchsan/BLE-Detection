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
import android.util.Log
import com.project.blebeacon.BuildConfig
import kotlinx.coroutines.flow.*
import org.json.JSONObject

class BleBackgroundService : Service() {
    private lateinit var bleManager: BleManager
    private lateinit var webSocketClient: WebSocketClient
    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())
    private var scanJob: Job? = null
    private lateinit var deviceId: String
    private val processedDevices = mutableListOf<BluetoothDeviceWrapper>()
    private val deviceHistory = mutableMapOf<String, MutableList<Pair<Long, Int>>>()
    private val lastSeenTime = mutableMapOf<String, Long>()
    private val deviceDisappearanceThreshold = 2000
    private val rssiThreshold = 5

    private var isDetectionEnabled = false
    private var samplingInterval = 5L

    companion object {
        private const val NOTIFICATION_ID = 123
        private const val CHANNEL_ID = "BleDetectionChannel"
        private const val CHANNEL_NAME = "BLE Device Scanner"

        private val _webSocketStatusFlow = MutableStateFlow(false)
        val webSocketStatusFlow: StateFlow<Boolean> = _webSocketStatusFlow.asStateFlow()

        private val _apIStatusFlow = MutableStateFlow(false)
        val apiStatusFlow: StateFlow<Boolean> = _apIStatusFlow.asStateFlow()

        private val _scanningStateFlow = MutableStateFlow(false)
        val scanningStateFlow: StateFlow<Boolean> = _scanningStateFlow.asStateFlow()

        private val _serviceRunning = MutableStateFlow(false)
        val serviceRunning: StateFlow<Boolean> = _serviceRunning.asStateFlow()
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification("Initializing BLE Scanner..."))

        _serviceRunning.value = true
        bleManager = BleManager(this)
        deviceId = android.provider.Settings.Secure.getString(
            contentResolver,
            android.provider.Settings.Secure.ANDROID_ID
        )

        initializeWebSocket()
        fetchConfigurationFromServer(deviceId)
    }

    private fun initializeWebSocket() {
        webSocketClient = WebSocketClient(
            "${BuildConfig.WEBSOCKET_URL}production?deviceid=$deviceId",
            deviceId
        )  // Pass deviceId to WebSocketClient
        { message ->
            handleWebSocketMessage(message)
        }.apply {
            setOnConnectCallback {
                Log.d("WebSocket", "Connected callback triggered")
                serviceScope.launch {
                    _webSocketStatusFlow.emit(true)
                    Log.d("WebSocket", "Status updated to: connected")
                }
            }
            setOnCloseCallback {
                Log.d("WebSocket", "Closed callback triggered")
                serviceScope.launch {
                    _webSocketStatusFlow.emit(false)
                    Log.d("WebSocket", "Status updated to: disconnected")
                }
            }
            setOnErrorCallback {
                Log.d("WebSocket", "Error callback triggered")
                serviceScope.launch {
                    _webSocketStatusFlow.emit(false)
                    Log.d("WebSocket", "Status updated to: disconnected (error)")
                }
            }
        }

        // Start the WebSocket connection
        webSocketClient.connect()
    }

    private fun fetchConfigurationFromServer(deviceId: String) {
        serviceScope.launch {
            try {
                val response = RetrofitInstance.apiService.getConfiguration(deviceId)
                if (response.isSuccessful) {
                    response.body()?.let { configResponse ->
                        val configurationData = configResponse.data.find { it.deviceid == deviceId }
                        configurationData?.let { config ->
                            isDetectionEnabled = config.is_detection_enabled
                            samplingInterval = config.sampling_interval.toLong()

                            if (isDetectionEnabled) {
                                startScanning()
                            } else {
                                stopScanning()
                            }
                        } ?: run {
                            // Default configuration jika tidak ditemukan data spesifik device
                            isDetectionEnabled = true
                            samplingInterval = 5L
                            startScanning()
                            Log.d("Configuration", "No config for device, using defaults")
                        }
                    }
                } else {
                    Log.e("Configuration", "Error: ${response.errorBody()?.string()}")
                }
            } catch (e: Exception) {
                Log.e("Configuration", "Failed to fetch configuration", e)
            }
        }
    }
    private fun handleWebSocketMessage(message: String) {
        Log.d("WebSocket", "Received message: $message")
        val jsonObject = JSONObject(message)

        // Periksa apakah ini adalah pembaruan konfigurasi
        if (jsonObject.has("updates")) {
            val updates = jsonObject.getJSONObject("updates")
            isDetectionEnabled = updates.getBoolean("is_detection_enabled")
            samplingInterval = updates.getLong("sampling_interval")

            Log.d("WebSocket", "Updated Config - isDetectionEnabled: $isDetectionEnabled, samplingInterval: $samplingInterval")

            // Terapkan logika untuk memulai atau menghentikan pemindaian
            if (isDetectionEnabled) {
                startScanning()
            } else {
                stopScanning()
            }
        }
    }

    private fun updateConfigurationToServer() {
        webSocketClient.sendMessage(
            JSONObject()
                .put("deviceid", deviceId)
                .put("updates", JSONObject()
                    .put("is_detection_enabled", isDetectionEnabled)
                    .put("sampling_interval", samplingInterval)
                ).toString()
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification("Initializing BLE Scanner..."))

        when (intent?.action) {
            "STOP_SCANNING" -> {
                stopSelf()
                return START_NOT_STICKY
            }
            "START_SCANNING" -> {
                startForeground(NOTIFICATION_ID, createNotification("Scanning for BLE devices..."))
                if (isDetectionEnabled) {
                    startScanning()
                }
            }
        }

        return START_STICKY
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
                // Hanya kirim jika ada perangkat yang terdeteksi
                if (detection.devices.isNotEmpty()) {
                    val request = DetectionRequest(
                        deviceid = deviceId,
                        timestamp = detection.timestamp,
                        device = detection.devices.size,
                        addresses = detection.devices.map { it.address },
                        rssi = detection.devices.map { it.rssi }
                    )

                    val response = RetrofitInstance.apiService.postDetection(request)
                    _apIStatusFlow.emit(response.isSuccessful)
                }
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
                description = "BLE Scanner Service"
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
            .setContentTitle("BLE Scanner")
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
        _serviceRunning.value = false
        _scanningStateFlow.value = false
        scanJob?.cancel()
        serviceScope.cancel()
        bleManager.stopScanning()
        super.onDestroy()
    }
}