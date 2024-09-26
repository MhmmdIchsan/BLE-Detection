package com.project.blebeacon

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
    private var sendToApi = true
    private val detections = mutableListOf<Detection>()
    private val dateFormat = SimpleDateFormat("dd-MMM-yyyy HH:mm:ss.SSS", Locale.getDefault())

    private val scanJob = Job()
    private val scanScope = CoroutineScope(Dispatchers.Default + scanJob)

    private lateinit var deviceId: String

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

        btnStartStop.setOnClickListener {
            if (isScanning) {
                stopScanning()
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
        detections.clear()
        detectionAdapter.updateDetections(detections)
        bleManager.startScanning()

        scanScope.launch {
            flow {
                while (isScanning) {
                    emit(Unit)
                    delay(1000) // Update every second
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

                    // Send data to API if switch is on
                    if (sendToApi) {
                        sendDetectionToApi(detection)
                    }
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
        scanJob.cancel()
        super.onDestroy()
    }
}