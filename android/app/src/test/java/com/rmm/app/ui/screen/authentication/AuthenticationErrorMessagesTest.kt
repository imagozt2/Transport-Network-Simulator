package com.rmm.app.ui.screen.authentication

import com.rmm.app.R
import com.rmm.app.core.network.ApiFailure
import com.rmm.app.core.network.NetworkFailureKind
import org.junit.Assert.assertEquals
import org.junit.Test

class AuthenticationErrorMessagesTest {
    @Test
    fun mapsAuthenticationHttpFailuresToSafeMessages() {
        assertEquals(R.string.auth_invalid_request, messageForStatus(400))
        assertEquals(R.string.auth_invalid_credentials, messageForStatus(401))
        assertEquals(R.string.auth_account_unavailable, messageForStatus(403))
        assertEquals(R.string.auth_email_already_registered, messageForStatus(409))
        assertEquals(R.string.auth_too_many_requests, messageForStatus(429))
        assertEquals(R.string.auth_service_unavailable, messageForStatus(503))
    }

    @Test
    fun distinguishesNetworkFailures() {
        assertEquals(
            R.string.auth_network_timeout,
            authenticationErrorMessage(ApiFailure.Network(NetworkFailureKind.TIMEOUT)),
        )
        assertEquals(
            R.string.auth_network_unreachable,
            authenticationErrorMessage(ApiFailure.Network(NetworkFailureKind.HOST_UNREACHABLE)),
        )
        assertEquals(
            R.string.auth_backend_unavailable,
            authenticationErrorMessage(ApiFailure.Network(NetworkFailureKind.CONNECTION)),
        )
    }

    private fun messageForStatus(status: Int): Int = authenticationErrorMessage(
        ApiFailure.Http(
            statusCode = status,
            problem = null,
            requestId = null,
            retryAfterSeconds = null,
        ),
    )
}
