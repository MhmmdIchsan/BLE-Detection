package com.project.blebeacon

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

class DeviceAdapter(private val context: Context) : RecyclerView.Adapter<DeviceAdapter.DeviceViewHolder>() {

    private val devices = mutableListOf<Device>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_device, parent, false)
        return DeviceViewHolder(view)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        val device = devices[position]
        holder.bind(device)
    }

    override fun getItemCount() = devices.size

    fun deviceExists(address: String): Boolean {
        return devices.any { it.address == address }
    }

    fun addDevice(device: BluetoothDevice, name: String, rssi: Int) {
        val newDevice = Device(name, "Unknown", device.address, rssi, System.currentTimeMillis())
        devices.add(newDevice)
        notifyItemInserted(devices.size - 1) // Notify that an item has been inserted
    }

    fun updateDevice(device: BluetoothDevice, name: String, rssi: Int) {
        val deviceIndex = devices.indexOfFirst { it.address == device.address }
        if (deviceIndex != -1) {
            devices[deviceIndex].name = name
            devices[deviceIndex].rssi = rssi
            devices[deviceIndex].lastUpdated = System.currentTimeMillis()

            notifyItemChanged(deviceIndex) // Notify that an item has changed
        }
    }

    fun getSignalLevel(rssi: Int, numLevels: Int): Int {
        if (rssi <= -100) {
            return 0
        } else if (rssi >= -50) {
            return numLevels - 1
        } else {
            val partitionSize = (50 - 100) / (numLevels - 1)
            return (rssi - (-100)) / partitionSize
        }
    }

    class DeviceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val deviceName: TextView = itemView.findViewById(R.id.device_name)
        private val deviceAddress: TextView = itemView.findViewById(R.id.device_address)
        private val deviceRssi: TextView = itemView.findViewById(R.id.device_rssi)
        private val signalStrengthBar: ImageView = itemView.findViewById(R.id.signal_strength) // Change this line

        fun bind(device: Device) {
            deviceName.text = device.name
            deviceAddress.text = device.address
            deviceRssi.text = "${device.rssi} dBm"

            val signalStrength = when {
                device.rssi >= -50 -> 4
                device.rssi >= -60 -> 3
                device.rssi >= -70 -> 2
                device.rssi >= -80 -> 1
                else -> 0
            }

            try {
                signalStrengthBar.setImageLevel(signalStrength)
            } catch (e: Exception) {
                TODO("Not yet implemented")
            }
        }
    }
}