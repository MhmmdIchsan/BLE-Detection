package com.project.blebeacon

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {

    private val PERMISSION_REQUEST_CODE = 1
    private lateinit var bluetoothHandler: BluetoothHandler
    private lateinit var deviceAdapter: DeviceAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bluetoothHandler = BluetoothHandler(this)
        checkPermissions()
        initRecyclerView()

        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = getString(R.string.app_name)
    }

    private fun checkPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_FINE_LOCATION,
            "android.permission.BLUETOOTH_SCAN" ,
            Manifest.permission.BLUETOOTH_CONNECT // Add this line

        )

        val permissionsToRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsToRequest.toTypedArray(), PERMISSION_REQUEST_CODE)
        } else {
            initializeBluetoothAndStartScanning()
        }
    }

    private fun initializeBluetoothAndStartScanning() {
        if (bluetoothHandler.initBluetooth()) {
            bluetoothHandler.startScanning { device, name, rssi -> // Add 'rssi' parameter here
                if (deviceAdapter.deviceExists(device.address)) {
                    deviceAdapter.updateDevice(device, name, rssi)
                } else {
                    deviceAdapter.addDevice(device, name, rssi)
                }
            }
        } else {
            Toast.makeText(this, "Bluetooth is not enabled", Toast.LENGTH_SHORT).show()
        }
    }

    private fun initRecyclerView() {
        val recyclerView: RecyclerView = findViewById(R.id.recycler_view)
        recyclerView.layoutManager = LinearLayoutManager(this)
        deviceAdapter = DeviceAdapter(this)
        recyclerView.adapter = deviceAdapter
        Log.d("MainActivity", "RecyclerView initialized")
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // The permission has been granted, you can proceed with accessing the feature or data
                initializeBluetoothAndStartScanning()
            } else {
                // The permission has been denied, you should disable the functionality that depends on this permission
                Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            bluetoothHandler.startScanning { device, name, rssi -> // Add 'name' parameter here
                if (deviceAdapter.deviceExists(device.address)) {
                    deviceAdapter.updateDevice(device, name, rssi)
                } else {
                    deviceAdapter.addDevice(device, name, rssi)
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        bluetoothHandler.stopScanning()
    }
}