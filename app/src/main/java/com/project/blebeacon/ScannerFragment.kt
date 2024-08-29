package com.project.blebeacon

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.project.blebeacon.R

class ScannerFragment : Fragment() {

    private lateinit var bleManager: BleManager
    private lateinit var deviceAdapter: DeviceAdapter
    private val PERMISSION_REQUEST_CODE = 1
    private val REQUEST_ENABLE_BT = 2

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_scanner, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        bleManager = BleManager(requireContext())
        checkAndRequestPermissions()
    }

    private fun setupRecyclerView() {
        val recyclerView: RecyclerView = view?.findViewById(R.id.deviceRecyclerView) ?: return
        deviceAdapter = DeviceAdapter { device ->
            // Handle device click if needed
            Toast.makeText(context, "Clicked: ${device.name}", Toast.LENGTH_SHORT).show()
        }
        recyclerView.adapter = deviceAdapter
        recyclerView.layoutManager = LinearLayoutManager(context)
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        val notGrantedPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(requireContext(), it) != PackageManager.PERMISSION_GRANTED
        }

        if (notGrantedPermissions.isNotEmpty()) {
            requestPermissions(notGrantedPermissions.toTypedArray(), PERMISSION_REQUEST_CODE)
        } else {
            Log.d("BLE", "All required permissions are granted")
            startScanning()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                startScanning()
            } else {
                Toast.makeText(context, "Permissions are required for BLE scanning", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun startScanning() {
        val bluetoothManager = requireContext().getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter = bluetoothManager.adapter

        if (bluetoothAdapter == null) {
            Log.e("BLE", "Device doesn't support Bluetooth")
            return
        }

        if (!bluetoothAdapter.isEnabled) {
            Log.e("BLE", "Bluetooth is not enabled")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                    val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                    startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
                } else {
                    Log.e("BLE", "BLUETOOTH_CONNECT permission not granted")
                    Toast.makeText(context, "Bluetooth permission not granted", Toast.LENGTH_LONG).show()
                }
            } else {
                val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
            }
            return
        }

        bleManager.startScanning { device ->
            // Tidak perlu melakukan apa-apa di sini, karena kita akan menangani pembaruan dalam startPeriodicUpdate
        }

        bleManager.startPeriodicUpdate { devices ->
            activity?.runOnUiThread {
                deviceAdapter.updateDevices(devices)
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == Activity.RESULT_OK) {
                startScanning()
            } else {
                Log.e("BLE", "Bluetooth not enabled")
                // Handle the case where the user didn't enable Bluetooth
            }
        }
    }

    override fun onDestroy() {
        bleManager.stopScanning()
        bleManager.stopPeriodicUpdate()
        super.onDestroy()
    }
}
