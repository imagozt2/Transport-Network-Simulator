package com.rmm.app.core.network

import com.rmm.app.core.network.model.ApiProblem

sealed interface ApiResult<out T> {
    data class Success<T>(
        val value: T,
        val metadata: ApiResponseMetadata,
    ) : ApiResult<T>

    data class Failure(val reason: ApiFailure) : ApiResult<Nothing>
}

data class ApiResponseMetadata(
    val statusCode: Int,
    val requestId: String?,
)

sealed interface ApiFailure {
    data class Http(
        val statusCode: Int,
        val problem: ApiProblem?,
        val requestId: String?,
        val retryAfterSeconds: Long?,
    ) : ApiFailure

    data class Network(val kind: NetworkFailureKind) : ApiFailure
    data object InvalidResponse : ApiFailure
    data object Serialization : ApiFailure
    data object Unexpected : ApiFailure
}

enum class NetworkFailureKind {
    TIMEOUT,
    HOST_UNREACHABLE,
    CONNECTION,
    OTHER,
}
