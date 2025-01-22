package com.project.blebeacon

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {
    @POST("bledemo/devices")
    suspend fun postDetection(@Body detectionRequest: DetectionRequest): Response<Unit>

    @GET("bledemo/locationinfo")
    suspend fun getConfiguration(@Query("deviceid") deviceid: String): Response<ConfigurationResponse>
}
