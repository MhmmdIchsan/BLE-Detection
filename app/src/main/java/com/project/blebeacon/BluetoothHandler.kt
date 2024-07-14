package com.project.blebeacon

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class BluetoothHandler(private val context: Context) {
    private val bluetoothScope = CoroutineScope(Dispatchers.IO)
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private val bleScanner = BleScanner(context)
    private lateinit var scanResultHandler: ScanResultHandler

    fun initBluetooth(): Boolean {
        if (bluetoothAdapter == null) {
            return false
        }
        if (!bluetoothAdapter.isEnabled) {
            return false
        }
        return true
    }

    fun startScanning(onDeviceFoundWithRssiAndName: (BluetoothDevice, String, Int) -> Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        bluetoothScope.launch {
            Log.d("BluetoothHandler", "Starting scan...")
            scanResultHandler = ScanResultHandler(context, onDeviceFoundWithRssiAndName)
            bleScanner.startScanning(scanResultHandler)
        }
    }

    fun stopScanning() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        bluetoothScope.cancel()
        bleScanner.stopScanning(scanResultHandler)
    }
}