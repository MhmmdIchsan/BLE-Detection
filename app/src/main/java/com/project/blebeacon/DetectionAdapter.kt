package com.project.blebeacon

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

class DetectionAdapter(private val onItemClick: (Detection) -> Unit) : RecyclerView.Adapter<DetectionAdapter.DetectionViewHolder>() {
    private var detections: List<Detection> = listOf()

    class DetectionViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTimestamp: TextView = view.findViewById(R.id.tvTimestamp)
        val tvDeviceCount: TextView = view.findViewById(R.id.tvDeviceCount)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DetectionViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_detection, parent, false)
        return DetectionViewHolder(view)
    }

    override fun onBindViewHolder(holder: DetectionViewHolder, position: Int) {
        val detection = detections[position]
        holder.tvTimestamp.text = detection.timestamp // Use the timestamp string directly
        holder.tvDeviceCount.text = "Devices: ${detection.devices.size}"
        holder.itemView.setOnClickListener { onItemClick(detection) }
    }

    override fun getItemCount() = detections.size

    fun updateDetections(newDetections: List<Detection>) {
        detections = newDetections
        notifyDataSetChanged()
    }
}