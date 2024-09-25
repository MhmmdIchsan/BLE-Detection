package com.project.blebeacon

data class DetectionRequest(
    val deviceid: String,
    val timestamp: String,
    val device: Int,
    val addresses: List<String>,
    val rssi: List<Int>
)