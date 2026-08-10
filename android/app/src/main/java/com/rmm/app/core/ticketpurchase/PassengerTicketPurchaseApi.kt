package com.rmm.app.core.ticketpurchase

import java.math.BigDecimal
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface PassengerTicketPurchaseApi {
    @POST("purchases")
    suspend fun purchase(
        @Header("Authorization") authorization: String,
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body request: PassengerTicketPurchaseRequest,
    ): Response<PassengerTicketPurchaseResponse>
}

data class PassengerTicketPurchaseRequest(
    val productCode: String,
    val configuration: PassengerTicketPurchaseConfiguration,
    val paymentMethod: String = "SIMULATED",
)

data class PassengerTicketPurchaseConfiguration(
    val originStationCode: String? = null,
    val destinationStationCode: String? = null,
    val tripCount: Int? = null,
    val dayCount: Int? = null,
    val rechargeAmount: BigDecimal? = null,
)

data class PassengerTicketPurchaseResponse(
    val code: String,
    val status: String,
    val productCode: String,
    val totalAmount: BigDecimal,
    val currency: String,
    val ticketCode: String,
    val requestedAt: String,
    val completedAt: String?,
)
