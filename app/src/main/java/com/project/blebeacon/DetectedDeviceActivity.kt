package com.project.blebeacon

import android.bluetooth.BluetoothDevice
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.Date

class DetectedDevicesActivity : AppCompatActivity() {

    private lateinit var rvDevices: RecyclerView
    private lateinit var tvTimestamp: TextView
    private lateinit var deviceAdapter: DeviceAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detected_devices)

        tvTimestamp = findViewById(R.id.tvTimestamp)
        rvDevices = findViewById(R.id.rvDevices)

        val timestamp = intent.getLongExtra("TIMESTAMP", 0L)
        val devices = intent.getSerializableExtra("DEVICES") as? ArrayList<BluetoothDeviceWrapper>

        tvTimestamp.text = "Timestamp: ${Date(timestamp)}"

        deviceAdapter = DeviceAdapter { device ->
            // Handle device click if needed
            Toast.makeText(this, "Clicked: ${device.name}", Toast.LENGTH_SHORT).show()
        }
        rvDevices.adapter = deviceAdapter
        rvDevices.layoutManager = LinearLayoutManager(this)

        devices?.let { deviceAdapter.submitList(it) }
    }
}