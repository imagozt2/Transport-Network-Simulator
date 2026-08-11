package com.rmm.app.core.ticketwallet

import com.rmm.app.core.network.ApiResult
import com.rmm.app.core.network.RMMApiCallExecutor
import com.rmm.app.core.network.RMMApiClient
import com.rmm.app.core.session.PassengerSession

class PassengerTicketWalletRepository(
    private val api: PassengerTicketWalletApi = RMMApiClient.create(PassengerTicketWalletApi::class.java),
    private val calls: RMMApiCallExecutor = RMMApiClient.calls(),
) {
    suspend fun tickets(
        session: PassengerSession,
        status: String? = null,
        productType: String? = null,
        limit: Int = 20,
        cursor: String? = null,
    ): ApiResult<PassengerTicketsResponse> = calls.execute {
        api.tickets(
            authorization = "Bearer ${session.accessToken}",
            status = status,
            productType = productType,
            limit = limit,
            cursor = cursor,
        )
    }

    suspend fun ticketQr(
        session: PassengerSession,
        ticketCode: String,
    ): ApiResult<PassengerTicketQr> = calls.execute {
        api.ticketQr("Bearer ${session.accessToken}", ticketCode)
    }
}
