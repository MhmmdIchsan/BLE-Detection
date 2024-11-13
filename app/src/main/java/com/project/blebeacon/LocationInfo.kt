package com.project.blebeacon

data class LocationInfo(
    val deviceid: String,
    val location: String,
    val latitude: Double,
    val longitude: Double,
    val is_detection_enabled: Boolean,
    val sampling_interval: Int
)
