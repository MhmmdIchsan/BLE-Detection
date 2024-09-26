package com.project.blebeacon

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.Serializable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.pow

class BleManager(private val context: Context) {
    private val bluetoothManager: BluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private val bluetoothLeScanner: BluetoothLeScanner? = bluetoothAdapter?.bluetoothLeScanner

    private val _scannedDevices = MutableStateFlow<List<BluetoothDeviceWrapper>>(emptyList())
    val scannedDevices: StateFlow<List<BluetoothDeviceWrapper>> = _scannedDevices

    private var isScanning = false
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = createBluetoothDeviceWrapper(result)
            updateScannedDevices(device)
        }
    }

    private val coroutineScope = CoroutineScope(Dispatchers.Default + Job())
    private var cleanupJob: Job? = null
    private val inactivityThreshold = 3000L // 3 seconds
    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    private var scanRestartJob: Job? = null

    fun startScanning() {
        if (!hasBluetoothPermission()) {
            Log.e("BLE", "Bluetooth permission not granted")
            return
        }

        if (isScanning) return

        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            bluetoothLeScanner?.startScan(null, scanSettings, scanCallback)
            isScanning = true
            Log.d("BLE", "Started continuous scanning for BLE devices")
            startPeriodicCleanup()
            startPeriodicScanRestart()
        } catch (e: SecurityException) {
            Log.e("BLE", "SecurityException when starting scan: ${e.message}")
        }
    }

    fun stopScanning() {
        if (!isScanning) return

        try {
            bluetoothLeScanner?.stopScan(scanCallback)
            isScanning = false
            stopPeriodicCleanup()
            stopPeriodicScanRestart()
            Log.d("BLE", "Stopped scanning")
        } catch (e: SecurityException) {
            Log.e("BLE", "SecurityException when stopping scan: ${e.message}")
        }
    }

    private fun startPeriodicScanRestart() {
        scanRestartJob = coroutineScope.launch {
            while (isActive) {
                delay(5 * 60 * 1000) // 5 minutes
                Log.d("BLE", "Performing periodic scan restart")
                stopScanning()
                delay(1000) // Wait for 1 second
                startScanning()
            }
        }
    }

    private fun stopPeriodicScanRestart() {
        scanRestartJob?.cancel()
        scanRestartJob = null
    }

    private fun startPeriodicCleanup() {
        cleanupJob = coroutineScope.launch {
            while (isActive) {
                delay(inactivityThreshold)
                removeInactiveDevices()
            }
        }
    }

    private fun stopPeriodicCleanup() {
        cleanupJob?.cancel()
        cleanupJob = null
    }

    private fun removeInactiveDevices() {
        val currentTime = System.currentTimeMillis()
        val (activeDevices, inactiveDevices) = _scannedDevices.value.partition { device ->
            currentTime - device.lastSeenTimestamp < inactivityThreshold
        }

        if (inactiveDevices.isNotEmpty()) {
            _scannedDevices.value = activeDevices
            logRemovedDevices(inactiveDevices)
        }
    }

    private fun logRemovedDevices(removedDevices: List<BluetoothDeviceWrapper>) {
        val currentTime = System.currentTimeMillis()
        removedDevices.forEachIndexed { index, device ->
        }
    }

    private fun updateScannedDevices(newDevice: BluetoothDeviceWrapper) {
        val currentDevices = _scannedDevices.value.toMutableList()
        val existingDeviceIndex = currentDevices.indexOfFirst { it.address == newDevice.address }

        if (existingDeviceIndex != -1) {
            currentDevices[existingDeviceIndex] = newDevice
        } else {
            currentDevices.add(newDevice)
        }

        _scannedDevices.value = currentDevices
    }

    private fun createBluetoothDeviceWrapper(result: ScanResult): BluetoothDeviceWrapper {
        return BluetoothDeviceWrapper(
            name = getDeviceName(result.device),
            address = result.device.address,
            rssi = result.rssi,
            deviceType = getDeviceType(result.device),
            distance = calculateDistance(result.rssi, result.txPower),
            lastSeenTimestamp = System.currentTimeMillis()
        )
    }

    private fun hasBluetoothPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun calculateDistance(rssi: Int, txPower: Int): Double {
        if (txPower == 127) {
            return calculateDistanceWithDefaultTxPower(rssi)
        }
        return 10.0.pow((txPower - rssi) / (10 * 2.0))
    }

    private fun calculateDistanceWithDefaultTxPower(rssi: Int): Double {
        val defaultTxPower = -59
        return 10.0.pow((defaultTxPower - rssi) / (10 * 2.0))
    }

    private fun getDeviceName(device: BluetoothDevice): String {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                    device.name ?: "Unknown"
                } else {
                    "Unknown (Permission not granted)"
                }
            } else {
                device.name ?: "Unknown"
            }
        } catch (e: SecurityException) {
            Log.e("BLE", "SecurityException when getting device name: ${e.message}")
            "Unknown (Security Exception)"
        }
    }

    private fun getDeviceType(device: BluetoothDevice): String {
        if (!hasBluetoothPermission()) {
            return "Unknown (No Permission)"
        }

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                    getDeviceTypeAndClass(device)
                } else {
                    "Unknown (Permission not granted)"
                }
            } else {
                getDeviceTypeAndClass(device)
            }
        } catch (e: SecurityException) {
            Log.e("BLE", "SecurityException when getting device type", e)
            "Unknown (Security Exception)"
        }
    }

    private fun getDeviceTypeAndClass(device: BluetoothDevice): String {
        val connectionType = try {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED ->
                    "Unknown (Permission not granted)"
                else -> when (device.type) {
                    BluetoothDevice.DEVICE_TYPE_CLASSIC -> "Classic"
                    BluetoothDevice.DEVICE_TYPE_LE -> "Low Energy"
                    BluetoothDevice.DEVICE_TYPE_DUAL -> "Dual Mode"
                    BluetoothDevice.DEVICE_TYPE_UNKNOWN -> "Unknown"
                    else -> "Unknown"
                }
            }
        } catch (e: SecurityException) {
            Log.e("BLE", "SecurityException when getting device type", e)
            "Unknown (Security Exception)"
        }

        val deviceClass = try {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED ->
                    "Unknown (Permission not granted)"
                else -> when (device.bluetoothClass.majorDeviceClass) {
                    BluetoothClass.Device.Major.COMPUTER -> "Computer"
                    BluetoothClass.Device.Major.PHONE -> "Phone"
                    BluetoothClass.Device.Major.PERIPHERAL -> "Peripheral"
                    BluetoothClass.Device.Major.AUDIO_VIDEO -> "Audio/Video"
                    BluetoothClass.Device.Major.WEARABLE -> "Wearable"
                    BluetoothClass.Device.Major.TOY -> "Toy"
                    BluetoothClass.Device.Major.HEALTH -> "Health"
                    BluetoothClass.Device.Major.UNCATEGORIZED -> "Uncategorized"
                    else -> "Other"
                }
            }
        } catch (e: SecurityException) {
            Log.e("BLE", "SecurityException when getting device class", e)
            "Unknown (Security Exception)"
        }

        return "$connectionType - $deviceClass"
    }
}

data class BluetoothDeviceWrapper(
    val name: String,
    val address: String,
    val rssi: Int,
    val deviceType: String,
    val distance: Double,
    val lastSeenTimestamp: Long
) : Serializable