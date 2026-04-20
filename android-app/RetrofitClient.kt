package com.example.falldetectionapp

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {

    // Android emulator
    private const val BASE_URL = "http://10.0.2.2:5050/"

    // Real device on same Wi-Fi example:
    // private const val BASE_URL = "http://192.168.1.xxx:5050/"

    val apiService: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}