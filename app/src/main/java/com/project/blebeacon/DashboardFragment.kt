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
    private lateinit var btnStartStop: Button
    private lateinit var tvDeviceCount: TextView
    private lateinit var tvTimestamp: TextView
    private lateinit var tvApiStatus: TextView
    private lateinit var tvDeviceId: TextView
    private lateinit var rvDetections: RecyclerView
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
        val manager = requireContext().getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return manager.getRunningServices(Integer.MAX_VALUE)
            .any { it.service.className == BleBackgroundService::class.java.name }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        btnStartStop = view.findViewById(R.id.btnStartStop)
        tvDeviceCount = view.findViewById(R.id.tvDeviceCount)
        tvTimestamp = view.findViewById(R.id.tvTimestamp)
        tvApiStatus = view.findViewById(R.id.tvApiStatus)
        tvDeviceId = view.findViewById(R.id.tvDeviceId)
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
        isScanning = isServiceRunning()
        updateScanButtonState()

        btnStartStop.setOnClickListener {
            if (isScanning) {
                showStopScanningConfirmation()
            } else {
                startScanning()
            }
        }
        updateApiStatus(false)

        startCollectingApiStatus()
        startCollectingState()

        // Add scanning state observer
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                BleBackgroundService.scanningStateFlow.collect { isScanning ->
                    this@DashboardFragment.isScanning = isScanning
                    updateScanButtonState()
                }
            }
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

    private fun updateScanButtonState() {
        btnStartStop.text = if (isScanning) "Stop" else "Start"
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
            updateScanButtonState()

            // Start the background service
            serviceIntent = Intent(requireContext(), BleBackgroundService::class.java)
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
        updateScanButtonState()

        // Send explicit stop command to service
        val stopIntent = Intent(requireContext(), BleBackgroundService::class.java).apply {
            action = "STOP_SCANNING"
        }
        requireContext().startService(stopIntent)

        // Then stop the service
        serviceIntent?.let { intent ->
            requireContext().stopService(intent)
        }

        // Clear UI
        tvDeviceCount.text = "0"
        tvTimestamp.text = ""
        detections.clear()
        detectionAdapter.updateDetections(detections)

        // Reset API status
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