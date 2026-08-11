package com.rmm.app.core.ticketwallet

import java.math.BigDecimal
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query

interface PassengerTicketWalletApi {
    @GET("tickets")
    suspend fun tickets(
        @Header("Authorization") authorization: String,
        @Query("status") status: String? = null,
        @Query("productType") productType: String? = null,
        @Query("limit") limit: Int = 20,
        @Query("cursor") cursor: String? = null,
    ): Response<PassengerTicketsResponse>

    @GET("tickets/{ticketCode}/qr")
    suspend fun ticketQr(
        @Header("Authorization") authorization: String,
        @Path("ticketCode") ticketCode: String,
    ): Response<PassengerTicketQr>

    @POST("ticket-links")
    suspend fun linkPhysicalTicket(
        @Header("Authorization") authorization: String,
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body request: PassengerTicketLinkRequest,
    ): Response<PassengerLinkedTicket>

    @GET("tickets/{ticketCode}/history")
    suspend fun ticketHistory(
        @Header("Authorization") authorization: String,
        @Path("ticketCode") ticketCode: String,
        @Query("limit") limit: Int = 20,
        @Query("cursor") cursor: String? = null,
    ): Response<PassengerTicketHistoryResponse>
}

data class PassengerTicketLinkRequest(val qrValue: String, val linkCode: String)

data class PassengerLinkedTicket(val code: String)

data class PassengerTicketHistoryResponse(
    val items: List<PassengerTicketHistoryItem> = emptyList(),
    val nextCursor: String? = null,
)

data class PassengerTicketHistoryItem(
    val type: String,
    val resultingStatus: String,
    val station: PassengerTicketStation? = null,
    val operationAmount: BigDecimal? = null,
    val balanceAfter: BigDecimal? = null,
    val remainingTripsAfter: Int? = null,
    val validFromAfter: String? = null,
    val validUntilAfter: String? = null,
    val currency: String = "EUR",
    val occurredAt: String,
)

data class PassengerTicketQr(
    val ticketCode: String,
    val qrValue: String,
    val credentialId: String,
    val expiresAt: String? = null,
)

data class PassengerTicketsResponse(
    val items: List<PassengerTicketSummary> = emptyList(),
    val nextCursor: String? = null,
)

data class PassengerTicketSummary(
    val code: String,
    val product: PassengerTicketProductSummary,
    val medium: String?,
    val status: String,
    val originStation: PassengerTicketStation? = null,
    val destinationStation: PassengerTicketStation? = null,
    val stationCount: Int? = null,
    val remainingTrips: Int? = null,
    val purchasedDays: Int? = null,
    val balanceAmount: BigDecimal = BigDecimal.ZERO,
    val currency: String = "EUR",
    val validFrom: String? = null,
    val validUntil: String? = null,
    val openJourney: Boolean = false,
    val issuedAt: String,
)

data class PassengerTicketProductSummary(
    val code: String,
    val name: String,
    val type: String,
)

data class PassengerTicketStation(
    val code: String,
    val name: String,
)
