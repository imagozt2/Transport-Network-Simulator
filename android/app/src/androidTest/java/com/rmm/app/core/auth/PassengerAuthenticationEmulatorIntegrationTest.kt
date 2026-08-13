package com.rmm.app.core.auth

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonParser
import com.rmm.app.core.environment.RMMApiConfiguration
import com.rmm.app.core.environment.RMMEnvironment
import com.rmm.app.core.network.ApiResult
import com.rmm.app.core.network.RMMApiClientFactory
import com.rmm.app.core.session.PassengerSessionStorage
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PassengerAuthenticationEmulatorIntegrationTest {
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
    fun registersLogsInPersistsTheSessionAndLogsOutFromTheEmulator() = runBlocking {
        server.enqueue(jsonResponse(201, registrationResponse()))
        server.enqueue(jsonResponse(201, sessionResponse()))
        server.enqueue(MockResponse().setResponseCode(204))
        val repository = PassengerAuthenticationRepository(context, apiFactory())

        val registration = repository.register(
            email = "maria.munoz@rmm.local",
            password = "ClaveSegura123",
            firstName = "María",
            lastName = "Muñoz",
        )
        assertTrue(registration is ApiResult.Success)
        assertEquals(false, (registration as ApiResult.Success).value.verificationRequired)

        val registrationRequest = server.takeRequest()
        assertEquals("POST", registrationRequest.method)
        assertEquals("/api/rmm-app/v1/auth/register", registrationRequest.path)
        JsonParser.parseString(registrationRequest.body.readUtf8()).asJsonObject.let { body ->
            assertEquals("María", body["firstName"].asString)
            assertEquals("Muñoz", body["lastName"].asString)
            assertEquals("es-ES", body["locale"].asString)
        }

        val authentication = repository.login("maria.munoz@rmm.local", "ClaveSegura123")
        assertTrue(authentication is AuthenticationResult.Authenticated)
        val session = (authentication as AuthenticationResult.Authenticated).session
        assertEquals("María", session.user.firstName)
        assertNotNull(PassengerSessionStorage.get(context).load())

        val loginRequest = server.takeRequest()
        assertEquals("POST", loginRequest.method)
        assertEquals("/api/rmm-app/v1/auth/sessions", loginRequest.path)
        JsonParser.parseString(loginRequest.body.readUtf8()).asJsonObject
            .getAsJsonObject("device").let { device ->
                assertEquals("ANDROID", device["platform"].asString)
                assertTrue(device["installationId"].asString.isNotBlank())
                assertTrue(device["name"].asString.isNotBlank())
            }

        assertEquals(LogoutResult.Completed, repository.logout(session))
        assertNull(PassengerSessionStorage.get(context).load())
        val logoutRequest = server.takeRequest()
        assertEquals("DELETE", logoutRequest.method)
        assertEquals("/api/rmm-app/v1/auth/sessions/current", logoutRequest.path)
        assertEquals("Bearer access-token-emulator", logoutRequest.getHeader("Authorization"))
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
            "publicId": "passenger-emulator-1",
            "email": "maria.munoz@rmm.local",
            "firstName": "María",
            "lastName": "Muñoz",
            "status": "ACTIVE",
            "locale": "es-ES"
          },
          "verificationRequired": false
        }
    """.trimIndent()

    private fun sessionResponse() = """
        {
          "accessToken": "access-token-emulator",
          "accessTokenExpiresAt": "2026-08-13T12:30:00Z",
          "refreshToken": "refresh-token-emulator",
          "refreshTokenExpiresAt": "2026-09-12T12:00:00Z",
          "user": {
            "publicId": "passenger-emulator-1",
            "email": "maria.munoz@rmm.local",
            "firstName": "María",
            "lastName": "Muñoz",
            "status": "ACTIVE",
            "locale": "es-ES"
          }
        }
    """.trimIndent()
}
