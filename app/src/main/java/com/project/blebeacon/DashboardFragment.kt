package com.project.blebeacon

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.text.SimpleDateFormat
import java.util.*

class DashboardFragment : Fragment() {

    private lateinit var btnStartStop: Button
    private lateinit var rvDetections: RecyclerView
    private lateinit var detectionAdapter: DetectionAdapter
    private lateinit var bleManager: BleManager
    private var isScanning = false
    private val detections = mutableListOf<Detection>()
    private val dateFormat = SimpleDateFormat("dd-MMM-yyyy HH:mm:ss.SSS", Locale.getDefault())

    private val scanJob = Job()
    private val scanScope = CoroutineScope(Dispatchers.Default + scanJob)

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
        detections.clear()
        detectionAdapter.updateDetections(detections)
        bleManager.startScanning()

        scanScope.launch {
            flow {
                while (isScanning) {
                    emit(Unit)
                    delay(500) // Emit every 500 milliseconds
                }
            }.conflate() // In case processing takes longer than 500ms
                .collect {
                    val currentTime = System.currentTimeMillis()
                    val formattedTime = dateFormat.format(Date(currentTime))
                    val latestDevices = bleManager.scannedDevices.value
                    val detection = Detection(formattedTime, latestDevices)

                    withContext(Dispatchers.Main) {
                        detections.add(0, detection)
                        if (detections.size > 10) {
                            detections.removeAt(detections.lastIndex)
                        }
                        detectionAdapter.updateDetections(detections)
                        rvDetections.scrollToPosition(0)
                    }

                    // Send data to API
                    sendDetectionToApi(detection)
                }
        }
    }

    private fun stopScanning() {
        isScanning = false
        btnStartStop.text = "Start"
        bleManager.stopScanning()
        scanJob.cancel()
    }

    private suspend fun sendDetectionToApi(detection: Detection) {
        try {
            val addresses = detection.devices.map { it.address }

            val devicesResponse = withContext(Dispatchers.IO) {
                RetrofitInstance.apiService.postDetection(
                    DetectionRequest(
                        timestamp = detection.timestamp,
                        device = detection.devices.size,
                        addresses = addresses
                    )
                )
            }

            if (devicesResponse.isSuccessful) {
                Log.d("API", "Successfully posted to /devices")
            } else {
                Log.e("API", "Error posting to /devices: ${devicesResponse.errorBody()?.string()}")
            }

        } catch (e: Exception) {
            Log.e("API", "Exception: ${e.message}")
        }
    }

    override fun onDestroy() {
        stopScanning()
        scanJob.cancel()
        super.onDestroy()
    }
}

data class Detection(val timestamp: String, val devices: List<BluetoothDeviceWrapper>)