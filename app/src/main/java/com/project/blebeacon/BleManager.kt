package com.project.blebeacon

import kotlinx.coroutines.*
import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import androidx.core.content.ContextCompat
import com.welie.blessed.BluetoothCentralManager
import com.welie.blessed.BluetoothCentralManagerCallback
import com.welie.blessed.BluetoothPeripheral
import com.welie.blessed.ScanFailure
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.seconds

class BleManager(private val context: Context) {
    private val coroutineScope = CoroutineScope(Dispatchers.Default)
    private val bluetoothManager: BluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private val central: BluetoothCentralManager
    private val discoveredDevices = ConcurrentHashMap<String, Pair<BluetoothDeviceWrapper, Long>>()
    private val handler = Handler(Looper.getMainLooper())
    private var onDeviceFoundCallback: ((BluetoothDeviceWrapper) -> Unit)? = null
    private val cachedDevices = mutableSetOf<String>() // Set to store cached device addresses
    private val updateHandler = Handler(Looper.getMainLooper())
    private val updateInterval = 2000L // 2 detik
    private var isUpdating = false
    private val deviceTimeout = 5000L // 5 detik, sesuaikan sesuai kebutuhan

    private val centralManagerCallback = object : BluetoothCentralManagerCallback() {
        override fun onDiscovered(peripheral: BluetoothPeripheral, scanResult: ScanResult) {
            handleDiscoveredPeripheral(peripheral, scanResult)
        }

        override fun onScanFailed(scanFailure: ScanFailure) {
            Log.e("BLE", "Scan failed: $scanFailure")
        }
    }
    init {
        central = BluetoothCentralManager(context, centralManagerCallback, handler)
    }

    fun startScanning(serviceUUID: UUID? = null, deviceName: String? = null, onDeviceFound: (BluetoothDeviceWrapper) -> Unit) {
        Log.d("BLE", "Starting BLE scan")
        onDeviceFoundCallback = onDeviceFound

        try {
            central.scanForPeripherals()
            Log.d("BLE", "Started scanning for peripherals")
        } catch (e: Exception) {
            Log.e("BLE", "Error starting scan: ${e.message}")
        }
    }

    fun isDeviceCached(address: String): Boolean {
        return cachedDevices.contains(address)
    }

    fun stopScanning() {
        central.stopScan()
        Log.d("BLE", "Stopped scanning")
    }

    private fun handleDiscoveredPeripheral(peripheral: BluetoothPeripheral, scanResult: ScanResult) {
        val deviceType = getDeviceType(scanResult.device)
        val deviceName = getDeviceName(peripheral, scanResult)

        val device = BluetoothDeviceWrapper(
            name = deviceName,
            address = peripheral.address,
            rssi = scanResult.rssi,
            deviceType = deviceType
        )

        discoveredDevices[device.address] = Pair(device, System.currentTimeMillis())
    }

    private fun getDeviceName(peripheral: BluetoothPeripheral, scanResult: ScanResult): String {
        return peripheral.name
            ?: scanResult.scanRecord?.deviceName
            ?: getBluetoothDeviceName(peripheral.address)
            ?: "Unknown (${peripheral.address})"
    }

    private fun getBluetoothDeviceName(address: String): String? {
        if (!hasBluetoothPermission()) {
            return null
        }
        return try {
            bluetoothAdapter?.getRemoteDevice(address)?.name
        } catch (e: SecurityException) {
            Log.e("BLE", "SecurityException when getting device name", e)
            null
        }
    }

    private fun hasBluetoothPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED
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

        coroutineScope.launch {
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

            val sortedDevices = discoveredDevices.values
                .map { it.first }
                .sortedByDescending { it.rssi }

            withContext(Dispatchers.Main) {
                onDeviceUpdate(sortedDevices)
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
            val connectionType = when (device.type) {
                BluetoothDevice.DEVICE_TYPE_CLASSIC -> "Classic"
                BluetoothDevice.DEVICE_TYPE_LE -> "Low Energy"
                BluetoothDevice.DEVICE_TYPE_DUAL -> "Dual Mode"
                BluetoothDevice.DEVICE_TYPE_UNKNOWN -> "Unknown"
                else -> "Unknown"
            }

            val deviceClass = when (device.bluetoothClass.majorDeviceClass) {
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

            "$connectionType - $deviceClass"
        } catch (e: SecurityException) {
            Log.e("BLE", "SecurityException when getting device type", e)
            "Unknown (Security Exception)"
        }
    }

    private fun getManufacturerData(scanResult: ScanResult): String? {
        val manufacturerData = scanResult.scanRecord?.manufacturerSpecificData
        if (manufacturerData != null && manufacturerData.size() > 0) {
            val manufacturerId = manufacturerData.keyAt(0)
            val data = manufacturerData.get(manufacturerId)
            return "Manufacturer ID: $manufacturerId, Data: ${data?.contentToString()}"
        }
        return null
    }
}

data class BluetoothDeviceWrapper(
    val name: String,
    val address: String,
    val rssi: Int,
    val deviceType: String,
)