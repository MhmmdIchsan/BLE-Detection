package com.project.blebeacon

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.Date

class DetectionAdapter(private val onItemClick: (Detection) -> Unit) : RecyclerView.Adapter<DetectionAdapter.ViewHolder>() {
    private var detections = listOf<Detection>()

    fun updateDetections(newDetections: List<Detection>) {
        val diffResult = DiffUtil.calculateDiff(DetectionDiffCallback(detections, newDetections))
        detections = newDetections
        diffResult.dispatchUpdatesTo(this)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_detection, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(detections[position])
    }

    override fun getItemCount() = detections.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvTimestamp: TextView = itemView.findViewById(R.id.tvTimestamp)
        private val tvDeviceCount: TextView = itemView.findViewById(R.id.tvDeviceCount)

        init {
            itemView.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) { // Periksa validitas posisi
                    onItemClick(detections[position])
                }
            }
        }

        fun bind(detection: Detection) {
            tvTimestamp.text = Date(detection.timestamp).toString()
            tvDeviceCount.text = "Devices detected: ${detection.devices.size}"
        }
    }

}

class DetectionDiffCallback(
    private val oldList: List<Detection>,
    private val newList: List<Detection>
) : DiffUtil.Callback() {

    override fun getOldListSize() = oldList.size

    override fun getNewListSize() = newList.size

    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int) =
        oldList[oldItemPosition].timestamp == newList[newItemPosition].timestamp

    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int) =
        oldList[oldItemPosition] == newList[newItemPosition]
}
