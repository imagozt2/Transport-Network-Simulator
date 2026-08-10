package com.rmm.app.core.ticketcatalog

import java.math.BigDecimal
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header

interface PassengerTicketProductApi {
    @GET("ticket-products")
    suspend fun products(
        @Header("Authorization") authorization: String,
    ): Response<PassengerTicketProductsResponse>
}

data class PassengerTicketProductsResponse(
    val items: List<PassengerTicketProduct> = emptyList(),
)

data class PassengerTicketProduct(
    val code: String,
    val name: String,
    val description: String?,
    val type: String,
    val basePrice: BigDecimal,
    val pricePerStation: BigDecimal,
    val pricePerTrip: BigDecimal,
    val pricePerDay: BigDecimal,
    val minTrips: Int?,
    val maxTrips: Int?,
    val minDays: Int?,
    val maxDays: Int?,
    val minRechargeAmount: BigDecimal?,
    val maxRechargeAmount: BigDecimal?,
    val requiresOriginDestination: Boolean,
    val usesTripBalance: Boolean,
    val usesDayValidity: Boolean,
    val usesMoneyBalance: Boolean,
    val rechargeable: Boolean,
    val currency: String,
)
