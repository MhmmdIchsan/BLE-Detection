package com.project.blebeacon

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.*
import kotlin.concurrent.fixedRateTimer

class DashboardFragment : Fragment() {

    private lateinit var btnStartStop: Button
    private lateinit var rvDetections: RecyclerView
    private lateinit var detectionAdapter: DetectionAdapter
    private lateinit var bleManager: BleManager
    private var isScanning = false
    private var timer: Timer? = null
    private val scannedDevices = mutableListOf<BluetoothDeviceWrapper>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_dashboard, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        btnStartStop = view.findViewById(R.id.btnStartStop)
        rvDetections = view.findViewById(R.id.rvDetections)

        bleManager = BleManager(requireContext())
        setupRecyclerView()

        btnStartStop.setOnClickListener {
            if (isScanning) {
                stopScanning()
            } else {
                startScanning()
            }
        }
    }

    private fun setupRecyclerView() {
        detectionAdapter = DetectionAdapter { detection ->
            // Handle item click
            val intent = Intent(requireContext(), DetectedDevicesActivity::class.java)
            intent.putExtra("TIMESTAMP", detection.timestamp)
            intent.putExtra("DEVICES", ArrayList(detection.devices))
            startActivity(intent)
        }
        rvDetections.adapter = detectionAdapter
        rvDetections.layoutManager = LinearLayoutManager(requireContext())
    }

    private fun startScanning() {
        isScanning = true
        btnStartStop.text = "Stop"
        scannedDevices.clear()
        bleManager.startScanning { device ->
            scannedDevices.add(device)
        }
        startPeriodicUpdate()
    }

    private fun stopScanning() {
        isScanning = false
        btnStartStop.text = "Start"
        bleManager.stopScanning()
        stopPeriodicUpdate()
    }

    private fun startPeriodicUpdate() {
        bleManager.startPeriodicUpdate { devices ->
            val detection = Detection(System.currentTimeMillis(), devices)
            activity?.runOnUiThread {
                detectionAdapter.addDetection(detection)
                rvDetections.scrollToPosition(0)
            }
        }
    }

    private fun stopPeriodicUpdate() {
        bleManager.stopPeriodicUpdate()
    }

    override fun onDestroy() {
        stopScanning()
        super.onDestroy()
    }
}

data class Detection(val timestamp: Long, val devices: List<BluetoothDeviceWrapper>)

