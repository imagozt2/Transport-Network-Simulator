package com.rmm.app.core.ticketwallet

import com.rmm.app.core.network.ApiResult
import com.rmm.app.core.network.RMMApiCallExecutor
import com.rmm.app.core.network.RMMApiClient
import com.rmm.app.core.session.PassengerSession
import java.util.UUID

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

    suspend fun linkPhysicalTicket(
        session: PassengerSession,
        qrValue: String,
        linkCode: String,
        idempotencyKey: String = UUID.randomUUID().toString(),
    ): ApiResult<PassengerLinkedTicket> = calls.execute {
        api.linkPhysicalTicket(
            authorization = "Bearer ${session.accessToken}",
            idempotencyKey = idempotencyKey,
            request = PassengerTicketLinkRequest(qrValue, linkCode),
        )
    }

    suspend fun ticketHistory(
        session: PassengerSession,
        ticketCode: String,
        cursor: String? = null,
    ): ApiResult<PassengerTicketHistoryResponse> = calls.execute {
        api.ticketHistory(
            authorization = "Bearer ${session.accessToken}",
            ticketCode = ticketCode,
            cursor = cursor,
        )
    }
}
