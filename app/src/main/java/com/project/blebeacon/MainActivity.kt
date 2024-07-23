package com.project.blebeacon

import com.project.blebeacon.BleManager
import com.project.blebeacon.DeviceAdapter
import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.project.blebeacon.R

class MainActivity : AppCompatActivity() {

    private lateinit var bleManager: BleManager
    private lateinit var deviceAdapter: DeviceAdapter
    private val PERMISSION_REQUEST_CODE = 1
    private val REQUEST_ENABLE_BT = 2

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        toolbar.title = "Blessed Beacon Scanner"
        toolbar.setTitleTextColor(ContextCompat.getColor(this, R.color.white))

        setupRecyclerView()
        bleManager = BleManager(this)
        checkAndRequestPermissions()
    }

    private fun setupRecyclerView() {
        val recyclerView: RecyclerView = findViewById(R.id.deviceRecyclerView)
        deviceAdapter = DeviceAdapter()
        recyclerView.adapter = deviceAdapter
        recyclerView.layoutManager = LinearLayoutManager(this)
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
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (notGrantedPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, notGrantedPermissions.toTypedArray(), PERMISSION_REQUEST_CODE)
        } else {
            Log.d("BLE", "All required permissions are granted")
            startScanning()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                startScanning()
            } else {
                Toast.makeText(this, "Permissions are required for BLE scanning", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun startScanning() {
        val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
        if (bluetoothAdapter == null) {
            Log.e("BLE", "Device doesn't support Bluetooth")
            return
        }
        if (!bluetoothAdapter.isEnabled) {
            Log.e("BLE", "Bluetooth is not enabled")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                    val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                    startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
                } else {
                    ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.BLUETOOTH_CONNECT), PERMISSION_REQUEST_CODE)
                }
            } else {
                val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
            }
            return
        }

        bleManager.startScanning { device ->
            // Callback saat perangkat ditemukan, tidak perlu melakukan apa-apa di sini
        }

        bleManager.startPeriodicUpdate { devices ->
            runOnUiThread {
                deviceAdapter.updateDevices(devices)
                (deviceAdapter.recyclerView.layoutManager as? LinearLayoutManager)?.scrollToPosition(0)
            }
        }
    }



    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == RESULT_OK) {
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