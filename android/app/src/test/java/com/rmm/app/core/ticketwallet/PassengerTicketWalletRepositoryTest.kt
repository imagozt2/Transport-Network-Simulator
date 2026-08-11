package com.rmm.app.core.ticketwallet

import com.google.gson.Gson
import com.rmm.app.core.network.ApiFailure
import com.rmm.app.core.network.ApiResult
import com.rmm.app.core.network.RMMApiCallExecutor
import com.rmm.app.core.session.PassengerSession
import com.rmm.app.core.session.PassengerSessionUser
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class PassengerTicketWalletRepositoryTest {

    @Test
    fun walletForwardsFiltersAndCursorWithPassengerAuthentication() = runBlocking {
        val api = RecordingWalletApi().apply {
            ticketsResponse = Response.success(PassengerTicketsResponse(
                items = listOf(ticket()),
                nextCursor = "RMM-TKT-002",
            ))
        }
        val repository = repository(api)

        val result = repository.tickets(session(), "ACTIVE", "MULTI_TRIP", 20, "RMM-TKT-003")

        assertTrue(result is ApiResult.Success)
        assertEquals("Bearer access-token", api.authorization)
        assertEquals("ACTIVE", api.status)
        assertEquals("MULTI_TRIP", api.productType)
        assertEquals("RMM-TKT-003", api.cursor)
        assertEquals(8, (result as ApiResult.Success).value.items.single().remainingTrips)
    }

    @Test
    fun physicalLinkSendsQrPrivateCodeAndIdempotencyKey() = runBlocking {
        val api = RecordingWalletApi().apply {
            linkResponse = Response.success(PassengerLinkedTicket("RMM-TKT-001"))
        }
        val repository = repository(api)

        val result = repository.linkPhysicalTicket(
            session(), "RMM:TICKET:1:signed", "ABCD-1234", "link-request-00000001"
        )

        assertTrue(result is ApiResult.Success)
        assertEquals("Bearer access-token", api.authorization)
        assertEquals("link-request-00000001", api.idempotencyKey)
        assertEquals(
            PassengerTicketLinkRequest("RMM:TICKET:1:signed", "ABCD-1234"),
            api.linkRequest,
        )
    }

    @Test
    fun physicalLinkPreservesAConflictReturnedByTheBackend() = runBlocking {
        val body = """{"status":409,"code":"TICKET_ALREADY_LINKED"}"""
            .toResponseBody("application/problem+json".toMediaType())
        val api = RecordingWalletApi().apply { linkResponse = Response.error(409, body) }

        val result = repository(api).linkPhysicalTicket(
            session(), "RMM:TICKET:1:signed", "ABCD", "link-request-00000002"
        )

        val failure = (result as ApiResult.Failure).reason as ApiFailure.Http
        assertEquals(409, failure.statusCode)
        assertEquals("TICKET_ALREADY_LINKED", failure.problem?.code)
    }

    @Test
    fun qrAndHistoryAreRequestedOnlyForTheSelectedTicket() = runBlocking {
        val api = RecordingWalletApi().apply {
            qrResponse = Response.success(PassengerTicketQr(
                "RMM-TKT-001", "RMM:TICKET:1:signed", "credential-1"
            ))
            historyResponse = Response.success(PassengerTicketHistoryResponse(
                listOf(PassengerTicketHistoryItem(
                    type = "ENTRY_ACCEPTED",
                    resultingStatus = "ACTIVE",
                    remainingTripsAfter = 7,
                    occurredAt = "2026-08-11T12:00:00",
                )),
                "operation-cursor-2",
            ))
        }
        val repository = repository(api)

        repository.ticketQr(session(), "RMM-TKT-001")
        val history = repository.ticketHistory(session(), "RMM-TKT-001", "operation-cursor-1")

        assertEquals("RMM-TKT-001", api.ticketCode)
        assertEquals("operation-cursor-1", api.historyCursor)
        assertEquals(7, (history as ApiResult.Success).value.items.single().remainingTripsAfter)
    }

    private class RecordingWalletApi : PassengerTicketWalletApi {
        var ticketsResponse: Response<PassengerTicketsResponse> = Response.success(PassengerTicketsResponse())
        var qrResponse: Response<PassengerTicketQr> = Response.success(
            PassengerTicketQr("RMM-TKT-001", "RMM:TICKET:1:signed", "credential")
        )
        var linkResponse: Response<PassengerLinkedTicket> = Response.success(PassengerLinkedTicket("RMM-TKT-001"))
        var historyResponse: Response<PassengerTicketHistoryResponse> = Response.success(PassengerTicketHistoryResponse())
        var authorization: String? = null
        var status: String? = null
        var productType: String? = null
        var cursor: String? = null
        var idempotencyKey: String? = null
        var linkRequest: PassengerTicketLinkRequest? = null
        var ticketCode: String? = null
        var historyCursor: String? = null

        override suspend fun tickets(authorization: String, status: String?, productType: String?, limit: Int, cursor: String?): Response<PassengerTicketsResponse> {
            this.authorization = authorization
            this.status = status
            this.productType = productType
            this.cursor = cursor
            return ticketsResponse
        }

        override suspend fun ticketQr(authorization: String, ticketCode: String): Response<PassengerTicketQr> {
            this.authorization = authorization
            this.ticketCode = ticketCode
            return qrResponse
        }

        override suspend fun linkPhysicalTicket(authorization: String, idempotencyKey: String, request: PassengerTicketLinkRequest): Response<PassengerLinkedTicket> {
            this.authorization = authorization
            this.idempotencyKey = idempotencyKey
            this.linkRequest = request
            return linkResponse
        }

        override suspend fun ticketHistory(authorization: String, ticketCode: String, limit: Int, cursor: String?): Response<PassengerTicketHistoryResponse> {
            this.authorization = authorization
            this.ticketCode = ticketCode
            this.historyCursor = cursor
            return historyResponse
        }
    }

    private fun repository(api: PassengerTicketWalletApi) = PassengerTicketWalletRepository(
        api, RMMApiCallExecutor(Gson())
    )

    private fun ticket() = PassengerTicketSummary(
        code = "RMM-TKT-001",
        product = PassengerTicketProductSummary("MULTI_TRIP", "Billete multiviaje", "MULTI_TRIP"),
        medium = "DIGITAL",
        status = "ACTIVE",
        remainingTrips = 8,
        balanceAmount = BigDecimal.ZERO,
        issuedAt = "2026-08-11T10:00:00",
    )

    private fun session() = PassengerSession(
        accessToken = "access-token",
        accessTokenExpiresAt = Instant.parse("2026-08-11T13:00:00Z"),
        refreshToken = "refresh-token",
        refreshTokenExpiresAt = Instant.parse("2026-08-12T12:00:00Z"),
        installationId = UUID.randomUUID().toString(),
        user = PassengerSessionUser(
            publicId = "passenger-1", email = "passenger@rmm.local",
            firstName = "Ana", lastName = "Viajera", status = "ACTIVE", locale = "es-ES",
        ),
    )
}
