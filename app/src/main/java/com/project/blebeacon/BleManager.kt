package com.project.blebeacon

import kotlinx.coroutines.*
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
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import java.io.Serializable
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.pow

class BleManager(private val context: Context) {
    private val scanJob = Job()
    private val scanScope = CoroutineScope(Dispatchers.Default + scanJob)
    private val bluetoothManager: BluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private val bluetoothLeScanner: BluetoothLeScanner? = bluetoothAdapter?.bluetoothLeScanner
    private val discoveredDevices = ConcurrentHashMap<String, Pair<BluetoothDeviceWrapper, Long>>()
    private val updateHandler = Handler(Looper.getMainLooper())
    private val updateInterval = 2000L
    private var isUpdating = false
    private val deviceTimeout = 5000L

    private var onDeviceFoundCallback: ((BluetoothDeviceWrapper) -> Unit)? = null

    private val leScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            handleDiscoveredDevice(result)
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e("BLE", "Scan failed with error code: $errorCode")
        }
    }

    fun startScanning(onDeviceFound: (BluetoothDeviceWrapper) -> Unit) {
        Log.d("BLE", "Starting BLE scan")
        onDeviceFoundCallback = onDeviceFound

        if (!hasBluetoothPermission()) {
            Log.e("BLE", "Bluetooth permission not granted")
            return
        }

        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
                    bluetoothLeScanner?.startScan(null, scanSettings, leScanCallback)
                    Log.d("BLE", "Started scanning for BLE devices")
                } else {
                    Log.e("BLE", "BLUETOOTH_SCAN permission not granted")
                }
            } else {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    bluetoothLeScanner?.startScan(null, scanSettings, leScanCallback)
                    Log.d("BLE", "Started scanning for BLE devices")
                } else {
                    Log.e("BLE", "ACCESS_FINE_LOCATION permission not granted")
                }
            }
        } catch (e: SecurityException) {
            Log.e("BLE", "SecurityException when starting scan: ${e.message}")
        } catch (e: Exception) {
            Log.e("BLE", "Error starting scan: ${e.message}")
        }
    }

    fun stopScanning() {
        if (!hasBluetoothPermission()) return
        try {
            bluetoothLeScanner?.stopScan(leScanCallback)
            Log.d("BLE", "Stopped scanning")
        } catch (e: SecurityException) {
            Log.e("BLE", "SecurityException when stopping scan: ${e.message}")
        }
    }

    private fun handleDiscoveredDevice(result: ScanResult) {
        val deviceType = getDeviceType(result.device)
        val deviceName = getDeviceName(result.device)
        val distance = calculateDistance(result.rssi, result.txPower)

        val device = BluetoothDeviceWrapper(
            name = deviceName,
            address = result.device.address,
            rssi = result.rssi,
            deviceType = deviceType,
            distance = distance
        )

        discoveredDevices[device.address] = Pair(device, System.currentTimeMillis())
        onDeviceFoundCallback?.invoke(device)
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

    private fun hasBluetoothPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun startPeriodicUpdate(onDeviceUpdate: (List<BluetoothDeviceWrapper>) -> Unit) {
        isUpdating = true
        updateDevices(onDeviceUpdate)
    }

    fun stopPeriodicUpdate() {
        isUpdating = false
        updateHandler.removeCallbacksAndMessages(null)
    }

    private fun updateDevices(onDeviceUpdate: (List<BluetoothDeviceWrapper>) -> Unit) {
        if (!isUpdating) return

        scanScope.launch {
            val currentTime = System.currentTimeMillis()
            val devicesToRemove = mutableListOf<String>()

            discoveredDevices.forEach { (address, pair) ->
                val (device, lastSeenTime) = pair
                if (currentTime - lastSeenTime > deviceTimeout) {
                    devicesToRemove.add(address)
                }
            }

            devicesToRemove.forEach { address ->
                discoveredDevices.remove(address)
            }

            val devices = discoveredDevices.values.map { it.first }

            withContext(Dispatchers.Main) {
                onDeviceUpdate(devices)
            }

            delay(updateInterval)
            updateDevices(onDeviceUpdate)
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
    val distance: Double
) : Serializable
