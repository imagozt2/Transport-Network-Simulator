package com.rmm.app.core.network

import com.google.gson.Gson
import com.google.gson.JsonParseException
import com.google.gson.stream.MalformedJsonException
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.UnknownHostException
import java.util.concurrent.CancellationException
import retrofit2.Response

class RMMApiCallExecutor(gson: Gson) {
    private val problemParser = RMMApiProblemParser(gson)

    suspend fun <T : Any> execute(call: suspend () -> Response<T>): ApiResult<T> =
        executeInternal(call) { response ->
            response.body()?.let {
                ApiResult.Success(it, response.metadata())
            } ?: ApiResult.Failure(ApiFailure.InvalidResponse)
        }

    suspend fun executeEmpty(call: suspend () -> Response<Unit>): ApiResult<Unit> =
        executeInternal(call) { response ->
            ApiResult.Success(Unit, response.metadata())
        }

    private suspend fun <T : Any, R> executeInternal(
        call: suspend () -> Response<T>,
        success: (Response<T>) -> ApiResult<R>,
    ): ApiResult<R> {
        return try {
            val response = call()
            if (response.isSuccessful) {
                success(response)
            } else {
                val problem = problemParser.parse(response.errorBody())
                ApiResult.Failure(
                    ApiFailure.Http(
                        statusCode = response.code(),
                        problem = problem,
                        requestId = response.requestId() ?: problem?.requestId,
                        retryAfterSeconds = response.headers()["Retry-After"]?.toLongOrNull(),
                    ),
                )
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: JsonParseException) {
            ApiResult.Failure(ApiFailure.Serialization)
        } catch (exception: IOException) {
            if (exception is MalformedJsonException) {
                ApiResult.Failure(ApiFailure.Serialization)
            } else {
                ApiResult.Failure(ApiFailure.Network(exception.networkKind()))
            }
        } catch (_: RuntimeException) {
            ApiResult.Failure(ApiFailure.Unexpected)
        }
    }

    private fun Response<*>.metadata() = ApiResponseMetadata(
        statusCode = code(),
        requestId = requestId(),
    )

    private fun Response<*>.requestId(): String? =
        headers()["X-Request-Id"] ?: headers()["X-Correlation-Id"]

    private fun IOException.networkKind(): NetworkFailureKind = when (this) {
        is InterruptedIOException -> NetworkFailureKind.TIMEOUT
        is UnknownHostException -> NetworkFailureKind.HOST_UNREACHABLE
        is ConnectException -> NetworkFailureKind.CONNECTION
        else -> NetworkFailureKind.OTHER
    }
}
