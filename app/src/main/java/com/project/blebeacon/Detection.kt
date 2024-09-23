package com.project.blebeacon

data class Detection(val timestamp: String, val devices: List<BluetoothDeviceWrapper>)