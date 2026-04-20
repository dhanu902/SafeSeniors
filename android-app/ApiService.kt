package com.example.falldetectionapp

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

interface ApiService {
    @POST("api/alerts")
    fun sendAlert(@Body alert: AlertRequest): Call<Map<String, Any>>
}