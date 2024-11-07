package com.project.blebeacon

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object BleSharedState {
    private val _detectionFlow = MutableSharedFlow<Detection>(replay = 1)
    val detectionFlow: SharedFlow<Detection> = _detectionFlow.asSharedFlow()

    suspend fun emitDetection(detection: Detection) {
        _detectionFlow.emit(detection)
    }
}