package com.project.blebeacon

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.project.blebeacon.R
import java.text.DecimalFormat

class DeviceAdapter(private val onItemClick: (BluetoothDeviceWrapper) -> Unit = {}) :
    ListAdapter<BluetoothDeviceWrapper, DeviceAdapter.DeviceViewHolder>(DeviceDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_device, parent, false)
        return DeviceViewHolder(view)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        val device = getItem(position)
        holder.bind(device)
        holder.itemView.setOnClickListener { onItemClick(device) }
    }

    fun updateDevices(newDevices: List<BluetoothDeviceWrapper>) {
        submitList(newDevices)
    }

    class DeviceViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val nameTextView: TextView = view.findViewById(R.id.deviceNameTextView)
        private val addressTextView: TextView = view.findViewById(R.id.deviceAddressTextView)
        private val rssiTextView: TextView = view.findViewById(R.id.deviceRssiTextView)
        private val typeTextView: TextView = view.findViewById(R.id.deviceTypeTextView)
        private val distanceTextView: TextView = view.findViewById(R.id.deviceDistanceTextView)

        fun bind(device: BluetoothDeviceWrapper) {
            nameTextView.text = device.name
            addressTextView.text = device.address
            rssiTextView.text = "${device.rssi} dBm"
            typeTextView.text = "Type: ${device.deviceType}"

            val distanceText = when {
                device.distance < 0 -> "Unknown"
                device.distance < 1 -> "${DecimalFormat("#.##").format(device.distance * 100)} cm"
                else -> "${DecimalFormat("#.##").format(device.distance)} m"
            }
            distanceTextView.text = "Distance: $distanceText"
        }
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