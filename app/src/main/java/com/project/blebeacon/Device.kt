package com.project.blebeacon

data class Device(
    var name: String, // Change this from 'val' to 'var'
    val type: String,
    val address: String,
    var rssi: Int,
    var lastUpdated: Long
)