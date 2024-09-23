package com.project.blebeacon

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
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
        tvDeviceCount = view.findViewById(R.id.tvDeviceCount)
        tvTimestamp = view.findViewById(R.id.tvTimestamp)
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
        detectionAdapter = DetectionAdapter()
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
                    delay(500) // Update every 500 milliseconds
                }
            }.conflate()
                .collect {
                    val currentTime = System.currentTimeMillis()
                    val formattedTime = dateFormat.format(Date(currentTime))
                    val latestDevices = bleManager.scannedDevices.value
                    val detection = Detection(formattedTime, latestDevices)

                    withContext(Dispatchers.Main) {
                        tvDeviceCount.text = latestDevices.size.toString()
                        tvTimestamp.text = formattedTime

                        detections.add(0, detection)
                        if (detections.size > 5) {
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