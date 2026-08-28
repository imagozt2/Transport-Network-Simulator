package com.rmm.app.core.ticketwallet

import com.rmm.app.core.network.ApiResult
import com.rmm.app.core.network.ApiResponseMetadata
import com.rmm.app.core.network.RMMApiCallExecutor
import com.rmm.app.core.network.RMMApiClient
import com.rmm.app.core.session.PassengerSession
import java.util.UUID

class PassengerTicketWalletRepository(
    private val api: PassengerTicketWalletApi = RMMApiClient.create(PassengerTicketWalletApi::class.java),
    private val calls: RMMApiCallExecutor = RMMApiClient.calls(),
) {
    suspend fun openDigitalJourneys(
        session: PassengerSession,
    ): ApiResult<List<PassengerTicketSummary>> {
        val pages = mutableListOf<PassengerTicketSummary>()
        val visitedCursors = mutableSetOf<String>()
        var cursor: String? = null
        var metadata: ApiResponseMetadata? = null

        do {
            when (val result = tickets(session = session, limit = 100, cursor = cursor)) {
                is ApiResult.Failure -> return result
                is ApiResult.Success -> {
                    metadata = metadata ?: result.metadata
                    pages += result.value.items
                    cursor = result.value.nextCursor
                }
            }
        } while (cursor != null && visitedCursors.add(cursor))

        return ApiResult.Success(
            value = pages.filter { ticket ->
                ticket.medium == "DIGITAL" && ticket.openJourney
            }.distinctBy(PassengerTicketSummary::code),
            metadata = metadata ?: ApiResponseMetadata(statusCode = 200, requestId = null),
        )
    }

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
