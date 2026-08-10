package com.rmm.app.core.ticketpurchase

import com.rmm.app.core.network.ApiResult
import com.rmm.app.core.network.RMMApiClient
import com.rmm.app.core.session.PassengerSession

class PassengerTicketPurchaseRepository {
    private val api = RMMApiClient.create(PassengerTicketPurchaseApi::class.java)
    private val calls = RMMApiClient.calls()

    suspend fun purchase(
        session: PassengerSession,
        idempotencyKey: String,
        request: PassengerTicketPurchaseRequest,
    ): ApiResult<PassengerTicketPurchaseResponse> = calls.execute {
        api.purchase("Bearer ${session.accessToken}", idempotencyKey, request)
    }
}
