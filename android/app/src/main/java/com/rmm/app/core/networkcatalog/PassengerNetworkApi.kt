package com.rmm.app.core.networkcatalog

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

interface PassengerNetworkApi {
    @GET("network/lines")
    suspend fun lines(
        @Header("Authorization") authorization: String,
    ): Response<PassengerNetworkLinesResponse>

    @GET("network/stations")
    suspend fun stations(
        @Header("Authorization") authorization: String,
        @Query("query") query: String? = null,
        @Query("lineCode") lineCode: String? = null,
    ): Response<PassengerNetworkStationsResponse>
}

data class PassengerNetworkLinesResponse(
    val items: List<PassengerNetworkLine> = emptyList(),
)

data class PassengerNetworkLine(
    val code: String,
    val name: String,
    val color: String,
    val terminals: List<String> = emptyList(),
    val active: Boolean,
)

data class PassengerNetworkStationsResponse(
    val items: List<PassengerNetworkStation> = emptyList(),
)

data class PassengerNetworkStation(
    val code: String,
    val name: String,
    val lineCodes: List<String> = emptyList(),
    val active: Boolean,
)
