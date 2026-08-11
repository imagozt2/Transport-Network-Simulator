package com.rmm.app.core.journeyhistory

import com.rmm.app.core.network.ApiResult
import com.rmm.app.core.network.RMMApiCallExecutor
import com.rmm.app.core.network.RMMApiClient
import com.rmm.app.core.session.PassengerSession
import java.math.BigDecimal
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

interface PassengerJourneyHistoryApi {
    @GET("journeys/history")
    suspend fun history(
        @Header("Authorization") authorization: String,
        @Query("limit") limit: Int = 20,
        @Query("cursor") cursor: String? = null,
    ): Response<PassengerJourneyHistoryResponse>
}

class PassengerJourneyHistoryRepository(
    private val api: PassengerJourneyHistoryApi =
        RMMApiClient.create(PassengerJourneyHistoryApi::class.java),
    private val calls: RMMApiCallExecutor = RMMApiClient.calls(),
) {
    suspend fun history(
        session: PassengerSession,
        cursor: String? = null,
    ): ApiResult<PassengerJourneyHistoryResponse> = calls.execute {
        api.history("Bearer ${session.accessToken}", cursor = cursor)
    }
}

data class PassengerJourneyHistoryResponse(
    val items: List<PassengerJourneyHistoryItem> = emptyList(),
    val nextCursor: String? = null,
)

data class PassengerJourneyHistoryItem(
    val code: String,
    val ticketCode: String,
    val productName: String,
    val productType: String,
    val origin: PassengerJourneyStation,
    val destination: PassengerJourneyStation? = null,
    val status: String,
    val stationCount: Int? = null,
    val fareAmount: BigDecimal? = null,
    val currency: String = "EUR",
    val openedAt: String,
    val endedAt: String? = null,
    val durationSeconds: Int? = null,
    val anomalous: Boolean = false,
)

data class PassengerJourneyStation(val code: String, val name: String)
