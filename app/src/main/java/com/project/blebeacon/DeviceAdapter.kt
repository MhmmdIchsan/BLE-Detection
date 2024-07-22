package com.project.blebeacon

import BluetoothDeviceWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.project.blebeacon.R

class DeviceAdapter : RecyclerView.Adapter<DeviceAdapter.DeviceViewHolder>() {

    private val devices = mutableListOf<BluetoothDeviceWrapper>()

    class DeviceViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val nameTextView: TextView = view.findViewById(R.id.deviceNameTextView)
        val addressTextView: TextView = view.findViewById(R.id.deviceAddressTextView)
        val rssiTextView: TextView = view.findViewById(R.id.deviceRssiTextView)
        val typeTextView: TextView = view.findViewById(R.id.deviceTypeTextView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_device, parent, false)
        return DeviceViewHolder(view)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        val device = devices[position]
        holder.nameTextView.text = device.name
        holder.addressTextView.text = device.address
        holder.rssiTextView.text = "RSSI: ${device.rssi} dBm"
        holder.typeTextView.text = "Type: ${device.deviceType}"
    }

    override fun getItemCount() = devices.size

    fun addDevice(device: BluetoothDeviceWrapper) {
        if (!devices.contains(device)) {
            devices.add(device)
            notifyItemInserted(devices.size - 1)
        }
    }
}