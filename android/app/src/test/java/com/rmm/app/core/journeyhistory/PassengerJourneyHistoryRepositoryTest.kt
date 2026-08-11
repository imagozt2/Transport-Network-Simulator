package com.rmm.app.core.journeyhistory

import com.google.gson.Gson
import com.rmm.app.core.network.ApiResult
import com.rmm.app.core.network.RMMApiCallExecutor
import com.rmm.app.core.session.PassengerSession
import com.rmm.app.core.session.PassengerSessionUser
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class PassengerJourneyHistoryRepositoryTest {

    @Test
    fun historyUsesPassengerAuthenticationAndForwardsTheCursor() = runBlocking {
        val api = RecordingApi().apply {
            response = Response.success(PassengerJourneyHistoryResponse(
                items = listOf(journey()),
                nextCursor = "RMM-JRN-002",
            ))
        }
        val repository = PassengerJourneyHistoryRepository(api, RMMApiCallExecutor(Gson()))

        val result = repository.history(session(), "RMM-JRN-003")

        assertTrue(result is ApiResult.Success)
        assertEquals("Bearer access-token", api.authorization)
        assertEquals("RMM-JRN-003", api.cursor)
        assertEquals("RMM-JRN-001", (result as ApiResult.Success).value.items.single().code)
        assertEquals("RMM-JRN-002", result.value.nextCursor)
    }

    private class RecordingApi : PassengerJourneyHistoryApi {
        var response = Response.success(PassengerJourneyHistoryResponse())
        var authorization: String? = null
        var cursor: String? = null

        override suspend fun history(
            authorization: String,
            limit: Int,
            cursor: String?,
        ): Response<PassengerJourneyHistoryResponse> {
            this.authorization = authorization
            this.cursor = cursor
            return response
        }
    }

    private fun journey() = PassengerJourneyHistoryItem(
        code = "RMM-JRN-001",
        ticketCode = "RMM-TKT-001",
        productName = "Saldo inteligente",
        productType = "SMART_BALANCE",
        origin = PassengerJourneyStation("ST001", "Aeropuerto"),
        destination = PassengerJourneyStation("ST010", "Gueto Norte"),
        status = "CLOSED",
        stationCount = 7,
        openedAt = "2026-08-11T10:00:00",
        endedAt = "2026-08-11T10:20:00",
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
