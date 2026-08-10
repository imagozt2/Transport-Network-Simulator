package com.rmm.app.core.network

import com.google.gson.Gson
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class RMMApiCallExecutorTest {
    private val executor = RMMApiCallExecutor(Gson())

    @Test
    fun httpErrorsPreserveProblemDetailsAndRequestId() = runBlocking {
        val body = """
            {
              "title": "No se puede iniciar sesion",
              "status": 401,
              "code": "INVALID_PASSENGER_CREDENTIALS",
              "detail": "Las credenciales no son validas",
              "requestId": "request-401"
            }
        """.trimIndent().toResponseBody("application/problem+json".toMediaType())

        val result = executor.execute<String> { Response.error(401, body) }

        assertTrue(result is ApiResult.Failure)
        val failure = (result as ApiResult.Failure).reason as ApiFailure.Http
        assertEquals(401, failure.statusCode)
        assertEquals("INVALID_PASSENGER_CREDENTIALS", failure.problem?.code)
        assertEquals("request-401", failure.requestId)
    }

    @Test
    fun unknownHostIsReportedAsUnreachableNetwork() = runBlocking {
        val result = executor.execute<String> { throw UnknownHostException("offline") }

        assertEquals(
            ApiFailure.Network(NetworkFailureKind.HOST_UNREACHABLE),
            (result as ApiResult.Failure).reason,
        )
    }

    @Test
    fun timeoutIsReportedWithoutLosingItsCategory() = runBlocking {
        val result = executor.execute<String> { throw SocketTimeoutException("timeout") }

        assertEquals(
            ApiFailure.Network(NetworkFailureKind.TIMEOUT),
            (result as ApiResult.Failure).reason,
        )
    }
}
