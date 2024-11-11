package com.project.blebeacon

data class DetectionRequest(
    val deviceid: String,
    val timestamp: String,
    val device: Int = 0,
    val addresses: List<String>? = null,
    val rssi: List<Int>? = null
)