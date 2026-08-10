package com.rmm.app.core.network.model

data class HealthResponse(
    val status: String,
    val database: String,
    val timestamp: String,
)
