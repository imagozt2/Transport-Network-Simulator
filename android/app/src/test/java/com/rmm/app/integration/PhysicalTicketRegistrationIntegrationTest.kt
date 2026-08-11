package com.rmm.app.integration

import com.google.gson.JsonParser
import com.rmm.app.core.environment.RMMApiConfiguration
import com.rmm.app.core.environment.RMMEnvironment
import com.rmm.app.core.network.ApiResult
import com.rmm.app.core.network.RMMApiClientFactory
import com.rmm.app.core.session.PassengerSession
import com.rmm.app.core.session.PassengerSessionUser
import com.rmm.app.core.ticketwallet.PassengerTicketWalletApi
import com.rmm.app.core.ticketwallet.PassengerTicketWalletRepository
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

class PhysicalTicketRegistrationIntegrationTest {

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
    fun passengerRegistersAScannedPhysicalTicketInTheWallet() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(201)
                .setHeader("Content-Type", "application/json")
                .setHeader("Location", "/api/rmm-app/v1/tickets/RMM-TKT-PHYSICAL-001")
                .setBody(
                    """
                    {
                      "code": "RMM-TKT-PHYSICAL-001",
                      "product": {
                        "code": "MULTI_TRIP",
                        "name": "Billete multiviaje",
                        "type": "MULTI_TRIP"
                      },
                      "medium": "PHYSICAL",
                      "status": "ACTIVE",
                      "purchasedTrips": 10,
                      "remainingTrips": 10,
                      "balanceAmount": 0.00,
                      "currency": "EUR",
                      "openJourney": null,
                      "issuedAt": "2026-08-11T12:00:00"
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
        val repository = PassengerTicketWalletRepository(
            api = factory.create(PassengerTicketWalletApi::class.java),
            calls = factory.calls,
        )

        val result = repository.linkPhysicalTicket(
            session = passengerSession(),
            qrValue = "RMM:TICKET:1:signed-physical-credential",
            linkCode = "ABCD-1234",
            idempotencyKey = "physical-link-request-000001",
        )

        assertTrue(result is ApiResult.Success)
        assertEquals("RMM-TKT-PHYSICAL-001", (result as ApiResult.Success).value.code)

        val recordedRequest = server.takeRequest()
        assertEquals("POST", recordedRequest.method)
        assertEquals("/api/rmm-app/v1/ticket-links", recordedRequest.path)
        assertEquals("Bearer passenger-access-token", recordedRequest.getHeader("Authorization"))
        assertEquals("physical-link-request-000001", recordedRequest.getHeader("Idempotency-Key"))
        assertEquals("android", recordedRequest.getHeader("X-RMM-Client"))

        val payload = JsonParser.parseString(recordedRequest.body.readUtf8()).asJsonObject
        assertEquals("RMM:TICKET:1:signed-physical-credential", payload["qrValue"].asString)
        assertEquals("ABCD-1234", payload["linkCode"].asString)
    }

    private fun passengerSession() = PassengerSession(
        accessToken = "passenger-access-token",
        accessTokenExpiresAt = Instant.parse("2026-08-11T14:00:00Z"),
        refreshToken = "passenger-refresh-token",
        refreshTokenExpiresAt = Instant.parse("2026-08-12T13:00:00Z"),
        installationId = UUID.fromString("d70b1c72-0c68-4666-a2a9-38108441dac7").toString(),
        user = PassengerSessionUser(
            publicId = "passenger-physical-1",
            email = "physical@rmm.local",
            firstName = "Lucía",
            lastName = "Viajera",
            status = "ACTIVE",
            locale = "es-ES",
        ),
    )
}
