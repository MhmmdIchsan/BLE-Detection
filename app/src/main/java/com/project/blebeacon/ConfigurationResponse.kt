package com.project.blebeacon

data class ConfigurationResponse(
    val message: String,
    val data: List<ConfigurationData>
)

data class ConfigurationData(
    val is_detection_enabled: Boolean,
    val sampling_interval: Double,
)