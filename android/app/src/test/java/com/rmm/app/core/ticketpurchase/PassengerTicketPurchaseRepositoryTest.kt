package com.rmm.app.core.ticketpurchase

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

class PassengerTicketPurchaseRepositoryTest {

    @Test
    fun purchaseSendsAuthenticationIdempotencyAndConfiguration() = runBlocking {
        val api = RecordingPurchaseApi(Response.success(purchaseResponse()))
        val repository = PassengerTicketPurchaseRepository(api, RMMApiCallExecutor(Gson()))
        val request = PassengerTicketPurchaseRequest(
            productCode = "MULTI_TRIP",
            configuration = PassengerTicketPurchaseConfiguration(tripCount = 12),
        )

        val result = repository.purchase(session(), "purchase-request-0001", request)

        assertTrue(result is ApiResult.Success)
        assertEquals("RMM-TKT-001", (result as ApiResult.Success).value.ticketCode)
        assertEquals("Bearer access-token", api.authorization)
        assertEquals("purchase-request-0001", api.idempotencyKey)
        assertEquals(request, api.request)
        assertEquals("SIMULATED", api.request?.paymentMethod)
    }

    @Test
    fun purchasePreservesAnIssuanceErrorFromTheApi() = runBlocking {
        val errorBody = """
            {
              "title": "No se ha podido emitir el billete",
              "status": 409,
              "code": "TICKET_ISSUANCE_FAILED",
              "detail": "La firma del billete no esta disponible"
            }
        """.trimIndent().toResponseBody("application/problem+json".toMediaType())
        val api = RecordingPurchaseApi(Response.error(409, errorBody))
        val repository = PassengerTicketPurchaseRepository(api, RMMApiCallExecutor(Gson()))

        val result = repository.purchase(
            session(),
            "purchase-request-0002",
            PassengerTicketPurchaseRequest(
                "TIME_PASS",
                PassengerTicketPurchaseConfiguration(dayCount = 7),
            ),
        )

        val failure = (result as ApiResult.Failure).reason as ApiFailure.Http
        assertEquals(409, failure.statusCode)
        assertEquals("TICKET_ISSUANCE_FAILED", failure.problem?.code)
    }

    private class RecordingPurchaseApi(
        private val response: Response<PassengerTicketPurchaseResponse>,
    ) : PassengerTicketPurchaseApi {
        var authorization: String? = null
        var idempotencyKey: String? = null
        var request: PassengerTicketPurchaseRequest? = null

        override suspend fun purchase(
            authorization: String,
            idempotencyKey: String,
            request: PassengerTicketPurchaseRequest,
        ): Response<PassengerTicketPurchaseResponse> {
            this.authorization = authorization
            this.idempotencyKey = idempotencyKey
            this.request = request
            return response
        }
    }

    private fun purchaseResponse() = PassengerTicketPurchaseResponse(
        code = "RMM-PUR-001",
        status = "COMPLETED",
        productCode = "MULTI_TRIP",
        totalAmount = BigDecimal("12.00"),
        currency = "EUR",
        ticketCode = "RMM-TKT-001",
        requestedAt = "2026-08-10T12:00:00",
        completedAt = "2026-08-10T12:00:00",
    )

    private fun session() = PassengerSession(
        accessToken = "access-token",
        accessTokenExpiresAt = Instant.parse("2026-08-10T13:00:00Z"),
        refreshToken = "refresh-token",
        refreshTokenExpiresAt = Instant.parse("2026-08-11T12:00:00Z"),
        installationId = UUID.randomUUID().toString(),
        user = PassengerSessionUser(
            publicId = "passenger-1",
            email = "passenger@rmm.local",
            firstName = "Ana",
            lastName = "Viajera",
            status = "ACTIVE",
            locale = "es-ES",
        ),
    )
}
