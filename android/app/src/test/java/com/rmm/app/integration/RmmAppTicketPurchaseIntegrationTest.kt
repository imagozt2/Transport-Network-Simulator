package com.rmm.app.integration

import com.google.gson.JsonParser
import com.rmm.app.core.environment.RMMApiConfiguration
import com.rmm.app.core.environment.RMMEnvironment
import com.rmm.app.core.network.ApiResult
import com.rmm.app.core.network.RMMApiClientFactory
import com.rmm.app.core.session.PassengerSession
import com.rmm.app.core.session.PassengerSessionUser
import com.rmm.app.core.ticketpurchase.PassengerTicketPurchaseApi
import com.rmm.app.core.ticketpurchase.PassengerTicketPurchaseConfiguration
import com.rmm.app.core.ticketpurchase.PassengerTicketPurchaseRepository
import com.rmm.app.core.ticketpurchase.PassengerTicketPurchaseRequest
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RmmAppTicketPurchaseIntegrationTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun passengerPurchasesASingleTripThroughTheRmmAppContract() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(201)
                .setHeader("Content-Type", "application/json")
                .setHeader("Location", "/api/rmm-app/v1/purchases/RMM-PUR-000101")
                .setBody(
                    """
                    {
                      "code": "RMM-PUR-000101",
                      "status": "COMPLETED",
                      "productCode": "SINGLE_TRIP",
                      "totalAmount": 0.85,
                      "currency": "EUR",
                      "ticketCode": "RMM-TKT-000101",
                      "requestedAt": "2026-08-11T10:00:00",
                      "completedAt": "2026-08-11T10:00:00"
                    }
                    """.trimIndent(),
                ),
        )
        val factory = RMMApiClientFactory(
            RMMApiConfiguration(
                environment = RMMEnvironment.LOCAL,
                baseUrl = server.url("/api/rmm-app/v1/"),
                debug = true,
            ),
        )
        val repository = PassengerTicketPurchaseRepository(
            api = factory.create(PassengerTicketPurchaseApi::class.java),
            calls = factory.calls,
        )

        val result = repository.purchase(
            session = passengerSession(),
            idempotencyKey = "rmm-app-purchase-000101",
            request = PassengerTicketPurchaseRequest(
                productCode = "SINGLE_TRIP",
                configuration = PassengerTicketPurchaseConfiguration(
                    originStationCode = "ST001",
                    destinationStationCode = "ST007",
                ),
            ),
        )

        assertTrue(result is ApiResult.Success)
        val purchase = (result as ApiResult.Success).value
        assertEquals("RMM-PUR-000101", purchase.code)
        assertEquals("COMPLETED", purchase.status)
        assertEquals("SINGLE_TRIP", purchase.productCode)
        assertEquals(0, purchase.totalAmount.compareTo("0.85".toBigDecimal()))
        assertEquals("RMM-TKT-000101", purchase.ticketCode)

        val recordedRequest = server.takeRequest()
        assertEquals("POST", recordedRequest.method)
        assertEquals("/api/rmm-app/v1/purchases", recordedRequest.path)
        assertEquals("Bearer passenger-access-token", recordedRequest.getHeader("Authorization"))
        assertEquals("rmm-app-purchase-000101", recordedRequest.getHeader("Idempotency-Key"))
        assertEquals("android", recordedRequest.getHeader("X-RMM-Client"))

        val payload = JsonParser.parseString(recordedRequest.body.readUtf8()).asJsonObject
        assertEquals("SINGLE_TRIP", payload["productCode"].asString)
        assertEquals("SIMULATED", payload["paymentMethod"].asString)
        assertEquals("ST001", payload["configuration"].asJsonObject["originStationCode"].asString)
        assertEquals("ST007", payload["configuration"].asJsonObject["destinationStationCode"].asString)
    }

    private fun passengerSession() = PassengerSession(
        accessToken = "passenger-access-token",
        accessTokenExpiresAt = Instant.parse("2026-08-11T12:00:00Z"),
        refreshToken = "passenger-refresh-token",
        refreshTokenExpiresAt = Instant.parse("2026-08-12T11:00:00Z"),
        installationId = UUID.fromString("83287cb0-40b6-4cdd-8fcb-2aa227bd5a2f").toString(),
        user = PassengerSessionUser(
            publicId = "passenger-101",
            email = "pasajero101@rmm.local",
            firstName = "Ana",
            lastName = "Viajera",
            status = "ACTIVE",
            locale = "es-ES",
        ),
    )
}
