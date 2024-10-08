package com.project.blebeacon

import android.app.AlertDialog
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Switch
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.text.SimpleDateFormat
import java.util.*

class DashboardFragment : Fragment() {

    private lateinit var btnStartStop: Button
    private lateinit var tvDeviceCount: TextView
    private lateinit var tvTimestamp: TextView
    private lateinit var tvApiStatus: TextView
    private lateinit var switchSendToApi: Switch
    private lateinit var rvDetections: RecyclerView
    private lateinit var detectionAdapter: DetectionAdapter
    private lateinit var bleManager: BleManager
    private var isScanning = false
    private var sendToApi = false
    private val detections = mutableListOf<Detection>()
    private val maxDetections = 5 // Set a maximum number of detections to keep
    private val dateFormat = SimpleDateFormat("dd-MMM-yyyy HH:mm:ss.SSS", Locale.getDefault())
    private var scanJob: Job? = null
    private val scanScope = CoroutineScope(Dispatchers.Default)
    private var lastScanRestartTime: Long = 0
    private lateinit var deviceId: String
    private val deviceHistory = mutableMapOf<String, MutableList<Pair<Long, Int>>>()
    private val processedDevices = mutableListOf<BluetoothDeviceWrapper>()
    private val lastSeenTime = mutableMapOf<String, Long>()
    private val deviceDisappearanceThreshold = 2000 // 5 seconds
    private val rssiThreshold = 5 // Threshold Dbm baru

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_dashboard, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        btnStartStop = view.findViewById(R.id.btnStartStop)
        tvDeviceCount = view.findViewById(R.id.tvDeviceCount)
        tvTimestamp = view.findViewById(R.id.tvTimestamp)
        tvApiStatus = view.findViewById(R.id.tvApiStatus)
        switchSendToApi = view.findViewById(R.id.switchSendToApi)
        rvDetections = view.findViewById(R.id.rvDetections)

        bleManager = BleManager(requireContext())
        setupRecyclerView()
        setupDeviceId()
        setupSendToApiSwitch()

        bleManager.setScanRestartCallback {
            scanScope.launch {
                withContext(Dispatchers.Main) {
                    lastScanRestartTime = System.currentTimeMillis()
                    detections.clear()
                    detectionAdapter.updateDetections(detections)
                    tvDeviceCount.text = "0"
                    tvTimestamp.text = ""
                }
            }
        }

        btnStartStop.setOnClickListener {
            if (isScanning) {
                showStopScanningConfirmation()
            } else {
                startScanning()
            }
        }
    }

    private fun setupDeviceId() {
        deviceId = Settings.Secure.getString(requireContext().contentResolver, Settings.Secure.ANDROID_ID)
    }

    private fun setupRecyclerView() {
        detectionAdapter = DetectionAdapter()
        rvDetections.adapter = detectionAdapter
        rvDetections.layoutManager = LinearLayoutManager(requireContext())
    }

    private fun setupSendToApiSwitch() {
        switchSendToApi.isChecked = sendToApi
        switchSendToApi.setOnCheckedChangeListener { _, isChecked ->
            sendToApi = isChecked
        }
    }

    private fun startScanning() {
        isScanning = true
        btnStartStop.text = "Stop"
        processedDevices.clear()
        deviceHistory.clear()
        lastScanRestartTime = System.currentTimeMillis()
        bleManager.startScanning()

        scanJob = scanScope.launch {
            flow {
                while (currentCoroutineContext().isActive) {
                    emit(Unit)
                    delay(1000) // Update every second
                }
            }
                .cancellable()
                .conflate()
                .collect {
                    val currentTime = System.currentTimeMillis()
                    val latestDevices = bleManager.scannedDevices.value

                    // Process and filter devices
                    processDevices(latestDevices, currentTime)

                    if (processedDevices.isNotEmpty()) {
                        val formattedTime = dateFormat.format(Date(currentTime))
                        val detection = Detection(formattedTime, processedDevices.toList())

//                        // Log the processed device data
//                        Log.d("ProcessedDevices", "Timestamp: $formattedTime, Devices: ${
//                            processedDevices.joinToString(", ") {
//                                "Address: ${it.address}, Name: ${it.name}, RSSI: ${it.rssi}"
//                            }
//                        }")

                        withContext(Dispatchers.Main) {
                            tvDeviceCount.text = processedDevices.size.toString()
                            tvTimestamp.text = formattedTime

                            detections.add(0, detection)
                            if (detections.size > maxDetections) {
                                detections.removeAt(detections.lastIndex)
                            }
                            detectionAdapter.updateDetections(detections)
                            rvDetections.scrollToPosition(0)
                        }

                        // Send data to API if switch is on
                        if (sendToApi) {
                            sendDetectionToApi(detection)
                        }
                    }
                }
        }
    }

    private fun processDevices(devices: List<BluetoothDeviceWrapper>, currentTime: Long) {
        val devicesToRemove = mutableListOf<String>()

        for (device in devices) {
            val history = deviceHistory.getOrPut(device.address) { mutableListOf() }
            history.add(Pair(currentTime, device.rssi))

            // Simpan hanya riwayat 5 detik terakhir
            val fiveSecondsAgo = currentTime - 2000
            deviceHistory[device.address] = history.filter { it.first >= fiveSecondsAgo }.toMutableList()

            // Perbarui waktu terakhir terlihat
            lastSeenTime[device.address] = currentTime

            // Periksa apakah perangkat telah bergerak
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

        // Hapus perangkat yang tidak bergerak
        processedDevices.removeAll { it.address in devicesToRemove }

        // Hapus perangkat yang tidak terlihat baru-baru ini
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

    private fun showStopScanningConfirmation() {
        AlertDialog.Builder(requireContext())
            .setTitle("Stop Scanning")
            .setMessage("Are you sure you want to stop scanning?")
            .setPositiveButton("Yes") { _, _ -> stopScanning() }
            .setNegativeButton("No", null)
            .show()
    }

    private fun stopScanning() {
        isScanning = false
        btnStartStop.text = "Start"
        bleManager.stopScanning()
        scanJob?.cancel()
        scanJob = null
    }

    private suspend fun sendDetectionToApi(detection: Detection) {
        try {
            val addresses = detection.devices.map { it.address }
            val rssiValues = detection.devices.map { it.rssi }

            val devicesResponse = withContext(Dispatchers.IO) {
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

            withContext(Dispatchers.Main) {
                if (devicesResponse.isSuccessful) {
                    Log.d("API", "Successfully posted to /devices")
                    updateApiStatus(true)
                } else {
                    Log.e("API", "Error posting to /devices: ${devicesResponse.errorBody()?.string()}")
                    updateApiStatus(false)
                }
            }
        } catch (e: Exception) {
            Log.e("API", "Exception: ${e.message}")
            withContext(Dispatchers.Main) {
                updateApiStatus(false)
            }
        }
    }

    private fun updateApiStatus(success: Boolean) {
        tvApiStatus.text = if (success) "Success" else "Fail"
        tvApiStatus.setTextColor(
            ContextCompat.getColor(
                requireContext(),
                if (success) android.R.color.holo_green_dark else android.R.color.holo_red_dark
            )
        )
    }

    override fun onDestroy() {
        stopScanning()
        scanJob?.cancel()
        scanScope.cancel()
        super.onDestroy()
    }
}