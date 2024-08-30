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
import android.widget.Button
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.*

class ScannerFragment : Fragment() {

    private lateinit var bleManager: BleManager
    private lateinit var deviceAdapter: DeviceAdapter
    private lateinit var scanButton: Button
    private lateinit var recyclerView: RecyclerView
    private val PERMISSION_REQUEST_CODE = 1
    private val REQUEST_ENABLE_BT = 2
    private var isScanning = false
    private val scanJob = Job()
    private val scanScope = CoroutineScope(Dispatchers.Default + scanJob)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_scanner, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupViews(view)
        bleManager = BleManager(requireContext())
        checkAndRequestPermissions()
    }

    private fun setupViews(view: View) {
        recyclerView = view.findViewById(R.id.deviceRecyclerView)
        scanButton = view.findViewById(R.id.scanButton)

        setupRecyclerView()

        scanButton.setOnClickListener {
            if (isScanning) {
                stopScanning()
            } else {
                startScanning()
            }
        }
    }

    private fun setupRecyclerView() {
        deviceAdapter = DeviceAdapter { device ->
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
            enableBluetooth()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                enableBluetooth()
            } else {
                Toast.makeText(context, "Permissions are required for BLE scanning", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun enableBluetooth() {
        val bluetoothManager = requireContext().getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter = bluetoothManager.adapter

        if (bluetoothAdapter == null) {
            Log.e("BLE", "Device doesn't support Bluetooth")
            return
        }

        if (!bluetoothAdapter.isEnabled) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
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
        } else {
            scanButton.isEnabled = true
        }
    }

    private fun startScanning() {
        isScanning = true
        scanButton.text = "Stop Scan"
        if (!isScanning) {
            bleManager.startScanning()  // Replacing scanForDevices() with startScanning()
            isScanning = true
        }
    }

    private fun stopScanning() {
        isScanning = false
        scanButton.text = "Start Scan"
        scanJob.cancel()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == Activity.RESULT_OK) {
                scanButton.isEnabled = true
            } else {
                Log.e("BLE", "Bluetooth not enabled")
                Toast.makeText(context, "Bluetooth is required for scanning", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onDestroy() {
        stopScanning()
        scanJob.cancel()
        super.onDestroy()
    }
}