package com.project.blebeacon

import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class DetectedDevicesActivity : AppCompatActivity() {

    private lateinit var rvDevices: RecyclerView
    private lateinit var tvTimestamp: TextView
    private lateinit var deviceAdapter: DeviceAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detected_devices)

        tvTimestamp = findViewById(R.id.tvTimestamp)
        rvDevices = findViewById(R.id.rvDevices)

        val timestamp = intent.getStringExtra("TIMESTAMP") ?: "Unknown"
        val devices = intent.getSerializableExtra("DEVICES") as? ArrayList<BluetoothDeviceWrapper>

        tvTimestamp.text = "Timestamp: $timestamp"

        deviceAdapter = DeviceAdapter { device ->
            // Handle device click if needed
            Toast.makeText(this, "Clicked: ${device.name}", Toast.LENGTH_SHORT).show()
        }

        rvDevices.adapter = deviceAdapter
        rvDevices.layoutManager = LinearLayoutManager(this)

        devices?.let { deviceAdapter.submitList(it) }
    }
}