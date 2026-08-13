package com.rmm.app.ui.screen.authentication

import androidx.annotation.StringRes
import com.rmm.app.R
import com.rmm.app.core.network.ApiFailure
import com.rmm.app.core.network.NetworkFailureKind

@StringRes
internal fun authenticationErrorMessage(failure: ApiFailure): Int = when (failure) {
    is ApiFailure.Http -> when (failure.statusCode) {
        400, 422 -> R.string.auth_invalid_request
        401 -> R.string.auth_invalid_credentials
        403 -> R.string.auth_account_unavailable
        409 -> R.string.auth_email_already_registered
        429 -> R.string.auth_too_many_requests
        in 500..599 -> R.string.auth_service_unavailable
        else -> R.string.auth_request_error
    }
    is ApiFailure.Network -> when (failure.kind) {
        NetworkFailureKind.TIMEOUT -> R.string.auth_network_timeout
        NetworkFailureKind.HOST_UNREACHABLE -> R.string.auth_network_unreachable
        NetworkFailureKind.CONNECTION -> R.string.auth_backend_unavailable
        NetworkFailureKind.OTHER -> R.string.auth_network_error
    }
    ApiFailure.InvalidResponse, ApiFailure.Serialization -> R.string.auth_invalid_response
    ApiFailure.Unexpected -> R.string.auth_request_error
}
