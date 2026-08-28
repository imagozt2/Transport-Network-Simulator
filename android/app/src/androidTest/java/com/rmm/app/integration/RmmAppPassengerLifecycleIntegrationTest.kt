package com.rmm.app.integration

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonParser
import com.rmm.app.core.auth.AuthenticationResult
import com.rmm.app.core.auth.PassengerAuthenticationRepository
import com.rmm.app.core.environment.RMMApiConfiguration
import com.rmm.app.core.environment.RMMEnvironment
import com.rmm.app.core.network.ApiResult
import com.rmm.app.core.network.RMMApiClientFactory
import com.rmm.app.core.session.PassengerSessionStorage
import com.rmm.app.core.ticketpurchase.PassengerTicketPurchaseApi
import com.rmm.app.core.ticketpurchase.PassengerTicketPurchaseConfiguration
import com.rmm.app.core.ticketpurchase.PassengerTicketPurchaseRepository
import com.rmm.app.core.ticketpurchase.PassengerTicketPurchaseRequest
import com.rmm.app.core.ticketwallet.PassengerTicketWalletApi
import com.rmm.app.core.ticketwallet.PassengerTicketWalletRepository
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RmmAppPassengerLifecycleIntegrationTest {
    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        PassengerSessionStorage.get(context).clear()
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        PassengerSessionStorage.get(context).clear()
        server.shutdown()
    }

    @Test
    fun passengerRegistersPurchasesAndFindsTheIssuedTicketInTheWallet() = runBlocking {
        server.enqueue(jsonResponse(201, registrationResponse()))
        server.enqueue(jsonResponse(201, sessionResponse()))
        server.enqueue(jsonResponse(201, purchaseResponse()))
        server.enqueue(jsonResponse(200, walletResponse()))

        val factory = apiFactory()
        val authentication = PassengerAuthenticationRepository(context, factory)
        val purchase = PassengerTicketPurchaseRepository(
            api = factory.create(PassengerTicketPurchaseApi::class.java),
            calls = factory.calls,
        )
        val wallet = PassengerTicketWalletRepository(
            api = factory.create(PassengerTicketWalletApi::class.java),
            calls = factory.calls,
        )

        val registration = authentication.register(
            email = "ana.viajera@rmm.local",
            password = "ClaveSegura123",
            firstName = "Ana",
            lastName = "Viajera",
        )
        assertTrue(registration is ApiResult.Success)
        assertEquals("/api/rmm-app/v1/auth/register", server.takeRequest().path)

        val login = authentication.login("ana.viajera@rmm.local", "ClaveSegura123")
        assertTrue(login is AuthenticationResult.Authenticated)
        val session = (login as AuthenticationResult.Authenticated).session
        assertEquals("passenger-functional-1", session.user.publicId)
        assertEquals("/api/rmm-app/v1/auth/sessions", server.takeRequest().path)

        val purchaseResult = purchase.purchase(
            session = session,
            idempotencyKey = "functional-purchase-0001",
            request = PassengerTicketPurchaseRequest(
                productCode = "MULTI_TRIP",
                configuration = PassengerTicketPurchaseConfiguration(tripCount = 10),
            ),
        )
        assertTrue(purchaseResult is ApiResult.Success)
        val issuedTicketCode = (purchaseResult as ApiResult.Success).value.ticketCode
        assertEquals("RMM-TKT-FUNCTIONAL-001", issuedTicketCode)

        val purchaseRequest = server.takeRequest()
        assertEquals("/api/rmm-app/v1/purchases", purchaseRequest.path)
        assertEquals("Bearer functional-access-token", purchaseRequest.getHeader("Authorization"))
        assertEquals("functional-purchase-0001", purchaseRequest.getHeader("Idempotency-Key"))
        JsonParser.parseString(purchaseRequest.body.readUtf8()).asJsonObject.let { body ->
            assertEquals("MULTI_TRIP", body["productCode"].asString)
            assertEquals(10, body["configuration"].asJsonObject["tripCount"].asInt)
        }

        val walletResult = wallet.tickets(session = session, status = "ACTIVE")
        assertTrue(walletResult is ApiResult.Success)
        val tickets = (walletResult as ApiResult.Success).value.items
        assertEquals(1, tickets.size)
        assertEquals(issuedTicketCode, tickets.single().code)
        assertEquals(10, tickets.single().remainingTrips)
        assertEquals("ACTIVE", tickets.single().status)

        val walletRequest = server.takeRequest()
        assertEquals("/api/rmm-app/v1/tickets?status=ACTIVE&limit=20", walletRequest.path)
        assertEquals("Bearer functional-access-token", walletRequest.getHeader("Authorization"))
    }

    private fun apiFactory() = RMMApiClientFactory(
        RMMApiConfiguration(
            environment = RMMEnvironment.LOCAL,
            baseUrl = server.url("/api/rmm-app/v1/"),
            debug = true,
        ),
    )

    private fun jsonResponse(code: Int, body: String) = MockResponse()
        .setResponseCode(code)
        .setHeader("Content-Type", "application/json; charset=UTF-8")
        .setBody(body)

    private fun registrationResponse() = """
        {
          "user": {
            "publicId": "passenger-functional-1",
            "email": "ana.viajera@rmm.local",
            "firstName": "Ana",
            "lastName": "Viajera",
            "status": "ACTIVE",
            "locale": "es-ES"
          },
          "verificationRequired": false
        }
    """.trimIndent()

    private fun sessionResponse() = """
        {
          "accessToken": "functional-access-token",
          "accessTokenExpiresAt": "2027-08-15T12:30:00Z",
          "refreshToken": "functional-refresh-token",
          "refreshTokenExpiresAt": "2027-09-15T12:30:00Z",
          "user": {
            "publicId": "passenger-functional-1",
            "email": "ana.viajera@rmm.local",
            "firstName": "Ana",
            "lastName": "Viajera",
            "status": "ACTIVE",
            "locale": "es-ES"
          }
        }
    """.trimIndent()

    private fun purchaseResponse() = """
        {
          "code": "RMM-PUR-FUNCTIONAL-001",
          "status": "COMPLETED",
          "productCode": "MULTI_TRIP",
          "totalAmount": 10.00,
          "currency": "EUR",
          "ticketCode": "RMM-TKT-FUNCTIONAL-001",
          "requestedAt": "2026-08-15T10:00:00Z",
          "completedAt": "2026-08-15T10:00:01Z"
        }
    """.trimIndent()

    private fun walletResponse() = """
        {
          "items": [
            {
              "code": "RMM-TKT-FUNCTIONAL-001",
              "product": {
                "code": "MULTI_TRIP",
                "name": "Billete multiviaje",
                "type": "MULTI_TRIP"
              },
              "medium": "DIGITAL",
              "status": "ACTIVE",
              "remainingTrips": 10,
              "balanceAmount": 0,
              "currency": "EUR",
              "openJourney": false,
              "issuedAt": "2026-08-15T10:00:01Z"
            }
          ],
          "nextCursor": null
        }
    """.trimIndent()
}
