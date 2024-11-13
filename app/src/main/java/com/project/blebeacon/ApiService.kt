package com.project.blebeacon

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface ApiService {
    @POST("bledemo/devices")
    suspend fun postDetection(@Body detectionRequest: DetectionRequest): Response<Unit>

    @GET("bledemo/locationinfo/{deviceId}")
    suspend fun getLocationInfo(@Path("deviceId") deviceId: String): Response<LocationInfo>
}
