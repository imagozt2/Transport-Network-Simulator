package com.rmm.app.core.network

import com.rmm.app.core.network.model.HealthResponse
import retrofit2.http.GET

interface RMMHealthApi {
    @GET("/api/health")
    suspend fun health(): HealthResponse
}
