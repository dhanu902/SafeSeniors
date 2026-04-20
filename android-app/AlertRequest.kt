package com.example.falldetectionapp

data class AlertRequest(
    val userId: String,
    val eventType: String,
    val status: String,
    val timestamp: String,
    val confidenceScore: Double
)