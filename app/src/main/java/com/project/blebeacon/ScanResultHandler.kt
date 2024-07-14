package com.project.blebeacon

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat

class ScanResultHandler(private val context: Context, private val onDeviceFound: (BluetoothDevice, String, Int) -> Unit) : ScanCallback() {
    override fun onScanResult(callbackType: Int, result: ScanResult) {
        super.onScanResult(callbackType, result)
        Log.d("ScanResultHandler", "Scan result: ${result.device.address}")

        // Check if the BLUETOOTH permission has been granted
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED) {
            // If the permission has been granted, you can access the device name
            val deviceName = result.scanRecord?.deviceName ?: result.device.name ?: "Unknown"
            Log.d("ScanResultHandler", "Device name: $deviceName")
            onDeviceFound(result.device, deviceName, result.rssi)
        } else {
            // If the permission has not been granted, handle this situation appropriately
            Log.d("ScanResultHandler", "BLUETOOTH permission not granted")
        }
    }
}