package com.project.blebeacon

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class DetectionAdapter : RecyclerView.Adapter<DetectionAdapter.DetectionViewHolder>() {
    private var detections: List<Detection> = emptyList()

    fun updateDetections(newDetections: List<Detection>) {
        detections = newDetections
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DetectionViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_detection, parent, false)
        return DetectionViewHolder(view)
    }

    override fun onBindViewHolder(holder: DetectionViewHolder, position: Int) {
        val detection = detections[position]
        holder.bind(detection)
    }

    override fun getItemCount() = detections.size

    class DetectionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvTimestamp: TextView = itemView.findViewById(R.id.tvTimestamp)
        private val tvDeviceCount: TextView = itemView.findViewById(R.id.tvDeviceCount)

        fun bind(detection: Detection) {
            tvTimestamp.text = detection.timestamp
            tvDeviceCount.text = "${detection.devices.size} devices"
        }
    }
}