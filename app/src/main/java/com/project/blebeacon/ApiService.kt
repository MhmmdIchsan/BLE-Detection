package com.project.blebeacon

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface ApiService {
    @POST("bledemo/devices")
    suspend fun postDetection(@Body detectionRequest: DetectionRequest): Response<Unit>
}
