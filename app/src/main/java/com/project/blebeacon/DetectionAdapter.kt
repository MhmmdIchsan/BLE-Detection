package com.project.blebeacon

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.Date

class DetectionAdapter(private val onItemClick: (Detection) -> Unit) : RecyclerView.Adapter<DetectionAdapter.ViewHolder>() {
    private val detections = mutableListOf<Detection>()

    fun addDetection(detection: Detection) {
        detections.add(0, detection)
        notifyItemInserted(0)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_detection, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val detection = detections[position]
        holder.bind(detection)
    }

    override fun getItemCount() = detections.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvTimestamp: TextView = itemView.findViewById(R.id.tvTimestamp)
        private val tvDeviceCount: TextView = itemView.findViewById(R.id.tvDeviceCount)

        init {
            itemView.setOnClickListener { onItemClick(detections[adapterPosition]) }
        }

        fun bind(detection: Detection) {
            tvTimestamp.text = Date(detection.timestamp).toString()
            tvDeviceCount.text = "Devices detected: ${detection.devices.size}"
        }
    }
}