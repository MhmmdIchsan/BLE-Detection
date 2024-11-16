package com.project.blebeacon

import android.app.ActivityManager
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Build
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect

class DashboardFragment : Fragment() {
    private var stateCollectionJob: Job? = null
    private var apiStatusJob: Job? = null
    private var serviceIntent: Intent? = null
    private lateinit var tvDeviceCount: TextView
    private lateinit var tvTimestamp: TextView
    private lateinit var tvApiStatus: TextView
    private lateinit var tvDeviceId: TextView
    private lateinit var rvDetections: RecyclerView
    private lateinit var tvWebSocketStatus: TextView
    private lateinit var tvScanStatus: TextView
    private lateinit var detectionAdapter: DetectionAdapter
    private lateinit var bleManager: BleManager
    private var isScanning = false
    private var sendToApi = false
    private val detections = mutableListOf<Detection>()
    private val maxDetections = 5 // Set a maximum number of detections to keep
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS", Locale.getDefault())
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

    private fun isServiceRunning(): Boolean {
        return runBlocking { BleBackgroundService.serviceRunning.first() }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvDeviceCount = view.findViewById(R.id.tvDeviceCount)
        tvTimestamp = view.findViewById(R.id.tvTimestamp)
        tvApiStatus = view.findViewById(R.id.tvApiStatus)
        tvDeviceId = view.findViewById(R.id.tvDeviceId)
        tvWebSocketStatus = view.findViewById(R.id.tvWebSocketStatus)
        tvScanStatus = view.findViewById(R.id.tvScanStatus)
        rvDetections = view.findViewById(R.id.rvDetections)

        bleManager = BleManager(requireContext())
        setupRecyclerView()
        setupDeviceId()

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
        updateWebSocketStatus(false)
        // Start collecting all states
        startCollectingStates()

        isScanning = isServiceRunning()
        updateApiStatus(false)
        updateScanStatus(isScanning)
    }

    private fun startCollectingStates() {
        viewLifecycleOwner.lifecycleScope.launch {
            // Collect WebSocket status
            launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    BleBackgroundService.webSocketStatusFlow
                        .onEach { isConnected ->
                            Log.d("DashboardFragment", "WebSocket status update: $isConnected")
                        }
                        .collect { isConnected ->
                            withContext(Dispatchers.Main) {
                                updateWebSocketStatus(isConnected)
                            }
                        }
                }
            }

            // Collect scanning status
            launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    BleBackgroundService.scanningStateFlow.collect { isScanning ->
                        withContext(Dispatchers.Main) {
                            this@DashboardFragment.isScanning = isScanning
                            updateScanStatus(isScanning)
                        }
                    }
                }
            }

            // Collect API status
            launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    BleBackgroundService.apiStatusFlow.collect { isActive ->
                        withContext(Dispatchers.Main) {
                            updateApiStatus(isActive)
                        }
                    }
                }
            }

            // Collect detection updates
            launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    BleSharedState.detectionFlow.collect { detection ->
                        withContext(Dispatchers.Main) {
                            updateUI(detection)
                        }
                    }
                }
            }

            // Collect service running status
            launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    BleBackgroundService.serviceRunning.collect { isRunning ->
                        if (!isRunning && isScanning) {
                            // Service was killed, restart it
                            startScanning()
                        }
                    }
                }
            }
        }
    }

    private fun updateScanStatus(isScanning: Boolean) {
        tvScanStatus.text = if (isScanning) "Started" else "Not Started"
        tvScanStatus.setTextColor(
            ContextCompat.getColor(
                requireContext(),
                if (isScanning) android.R.color.holo_green_dark else android.R.color.holo_red_dark
            )
        )
    }

    private fun updateWebSocketStatus(isConnected: Boolean) {
        try {
            if (!isAdded) return  // Check if fragment is attached to activity

            tvWebSocketStatus?.let { textView ->
                textView.text = if (isConnected) "Connected" else "Disconnected"
                textView.setTextColor(
                    ContextCompat.getColor(
                        requireContext(),
                        if (isConnected) android.R.color.holo_green_dark else android.R.color.holo_red_dark
                    )
                )
                Log.d("DashboardFragment", "WebSocket UI updated: ${textView.text}")
            }
        } catch (e: Exception) {
            Log.e("DashboardFragment", "Error updating WebSocket status", e)
        }
    }

    private fun startCollectingApiStatus() {
        apiStatusJob = viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                BleBackgroundService.apiStatusFlow.collect { isActive ->
                    updateApiStatus(isActive)
                }
            }
        }
    }

    private fun startCollectingState() {
        stateCollectionJob = viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                BleSharedState.detectionFlow.collect { detection ->
                    updateUI(detection)
                }
            }
        }
    }

    private fun setupDeviceId() {
        deviceId = Settings.Secure.getString(requireContext().contentResolver, Settings.Secure.ANDROID_ID)
        tvDeviceId.text = deviceId
    }

    private fun setupRecyclerView() {
        detectionAdapter = DetectionAdapter()
        rvDetections.adapter = detectionAdapter
        rvDetections.layoutManager = LinearLayoutManager(requireContext())
    }

    fun startScanning() {
        if (!isScanning) {
            isScanning = true

            // Start the background service with explicit action
            serviceIntent = Intent(requireContext(), BleBackgroundService::class.java).apply {
                action = "START_SCANNING"
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                requireContext().startForegroundService(serviceIntent)
            } else {
                requireContext().startService(serviceIntent)
            }
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

        // Send explicit stop command to service
        val stopIntent = Intent(requireContext(), BleBackgroundService::class.java).apply {
            action = "STOP_SCANNING"
        }
        requireContext().startService(stopIntent)

        // Clear UI
        tvDeviceCount.text = "0"
        tvTimestamp.text = ""
        detections.clear()
        detectionAdapter.updateDetections(detections)
        updateApiStatus(false)
    }

    private fun updateUI(detection: Detection) {
        tvDeviceCount.text = detection.devices.size.toString()
        tvTimestamp.text = detection.timestamp

        detections.add(0, detection)
        if (detections.size > maxDetections) {
            detections.removeAt(detections.lastIndex)
        }
        detectionAdapter.updateDetections(detections)
        rvDetections.scrollToPosition(0)

        // Update API status if needed
        if (sendToApi) {
            // You might want to handle API status updates differently
            updateApiStatus(true)
        }
    }

    private fun updateApiStatus(isActive: Boolean) {
        tvApiStatus.text = if (isActive) "Active" else "Failed"
        tvApiStatus.setTextColor(
            ContextCompat.getColor(
                requireContext(),
                if (isActive) android.R.color.holo_green_dark else android.R.color.holo_red_dark
            )
        )
    }

    override fun onDestroyView() {
        stateCollectionJob?.cancel()
        apiStatusJob?.cancel()
        super.onDestroyView()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopScanning()
        scanJob?.cancel()
        scanScope.cancel()
    }
}