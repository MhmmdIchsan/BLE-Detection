package com.project.blebeacon

data class DetectionRequest(
    val timestamp: String,
    val device: Int,
    val addresses: List<String>
)
