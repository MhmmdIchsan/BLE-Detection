package com.project.blebeacon

import com.project.blebeacon.BleManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.project.blebeacon.R

class DeviceAdapter : ListAdapter<BluetoothDeviceWrapper, DeviceAdapter.DeviceViewHolder>(DeviceDiffCallback()) {
    lateinit var recyclerView: RecyclerView

    class DeviceViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val nameTextView: TextView = view.findViewById(R.id.deviceNameTextView)
        val addressTextView: TextView = view.findViewById(R.id.deviceAddressTextView)
        val rssiTextView: TextView = view.findViewById(R.id.deviceRssiTextView)
        val typeTextView: TextView = view.findViewById(R.id.deviceTypeTextView)
        val signalStrengthImageView: ImageView = view.findViewById(R.id.signal_strength)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_device, parent, false)
        return DeviceViewHolder(view)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        val device = getItem(position)
        holder.nameTextView.text = device.name
        holder.addressTextView.text = device.address
        holder.rssiTextView.text = "${device.rssi} dBm"
        holder.typeTextView.text = "Type: ${device.deviceType}"

        // Set signal strength image based on RSSI
        val signalStrength = when {
            device.rssi >= -50 -> 4
            device.rssi >= -60 -> 3
            device.rssi >= -70 -> 2
            device.rssi >= -80 -> 1
            else -> 0
        }
        holder.signalStrengthImageView.setImageLevel(signalStrength)
    }

    fun updateDevices(newDevices: List<BluetoothDeviceWrapper>) {
        submitList(newDevices)
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        this.recyclerView = recyclerView
    }
}

class DeviceDiffCallback : DiffUtil.ItemCallback<BluetoothDeviceWrapper>() {
    override fun areItemsTheSame(oldItem: BluetoothDeviceWrapper, newItem: BluetoothDeviceWrapper): Boolean {
        return oldItem.address == newItem.address
    }

    override fun areContentsTheSame(oldItem: BluetoothDeviceWrapper, newItem: BluetoothDeviceWrapper): Boolean {
        return oldItem == newItem
    }
}