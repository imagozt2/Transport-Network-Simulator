package com.rmm.app.core.auth

import android.content.Context
import com.rmm.app.core.network.ApiFailure
import com.rmm.app.core.network.ApiResult
import com.rmm.app.core.network.RMMApiClient
import com.rmm.app.core.network.RMMApiClientFactory
import com.rmm.app.core.session.PassengerSession
import com.rmm.app.core.session.PassengerSessionStorage
import com.rmm.app.core.session.PassengerSessionUser
import java.time.Instant

class PassengerAuthenticationRepository(
    context: Context,
    apiFactory: RMMApiClientFactory? = null,
) {
    private val applicationContext = context.applicationContext
    private val api = apiFactory?.create(PassengerAuthenticationApi::class.java)
        ?: RMMApiClient.create(PassengerAuthenticationApi::class.java)
    private val calls = apiFactory?.calls ?: RMMApiClient.calls()
    private val installation = PassengerInstallation(applicationContext)
    private val sessionStore = PassengerSessionStorage.get(applicationContext)

    suspend fun register(
        email: String,
        password: String,
        firstName: String,
        lastName: String,
    ): ApiResult<PassengerRegistrationResponse> = calls.execute {
        api.register(
            PassengerRegistrationRequest(
                email = email.trim(),
                password = password,
                firstName = firstName.trim(),
                lastName = lastName.trim(),
            ),
        )
    }

    suspend fun registerAndAuthenticate(
        email: String,
        password: String,
        firstName: String,
        lastName: String,
    ): PassengerRegistrationResult {
        return when (val registration = register(email, password, firstName, lastName)) {
            is ApiResult.Failure -> PassengerRegistrationResult.Failure(registration.reason)
            is ApiResult.Success -> {
                if (registration.value.verificationRequired) {
                    PassengerRegistrationResult.VerificationRequired(registration.value.user.email)
                } else {
                    when (val authentication = login(email, password)) {
                        is AuthenticationResult.Authenticated -> {
                            PassengerRegistrationResult.Authenticated(authentication.session)
                        }
                        is AuthenticationResult.Failure -> {
                            PassengerRegistrationResult.Failure(authentication.reason)
                        }
                        AuthenticationResult.StorageFailure -> {
                            PassengerRegistrationResult.StorageFailure
                        }
                    }
                }
            }
        }
    }

    suspend fun login(email: String, password: String): AuthenticationResult {
        return when (val result = calls.execute {
            api.login(
                PassengerLoginRequest(
                    email = email.trim(),
                    password = password,
                    device = PassengerDeviceRequest(
                        installationId = installation.id,
                        name = installation.deviceName,
                    ),
                ),
            )
        }) {
            is ApiResult.Failure -> AuthenticationResult.Failure(result.reason)
            is ApiResult.Success -> {
                try {
                    val session = result.value.toDomain(installation.id)
                    sessionStore.save(session)
                    AuthenticationResult.Authenticated(session)
                } catch (_: Exception) {
                    AuthenticationResult.StorageFailure
                }
            }
        }
    }

    suspend fun logout(session: PassengerSession): LogoutResult {
        val remoteResult = calls.executeEmpty {
            api.logout(authorization = "Bearer ${session.accessToken}")
        }

        return try {
            sessionStore.clear()
            when (remoteResult) {
                is ApiResult.Success -> LogoutResult.Completed
                is ApiResult.Failure -> LogoutResult.CompletedLocally
            }
        } catch (_: Exception) {
            LogoutResult.LocalStorageFailure
        }
    }

    suspend fun renewSession(session: PassengerSession): SessionRenewalResult {
        if (!session.canBeRefreshed()) {
            clearInvalidSession()
            return SessionRenewalResult.Invalidated
        }

        return when (val result = calls.execute {
            api.refresh(
                PassengerSessionRefreshRequest(
                    refreshToken = session.refreshToken,
                    installationId = session.installationId,
                ),
            )
        }) {
            is ApiResult.Success -> persistRenewedSession(result.value, session.installationId)
            is ApiResult.Failure -> {
                val status = (result.reason as? ApiFailure.Http)?.statusCode
                if (status == 401 || status == 403) {
                    clearInvalidSession()
                    SessionRenewalResult.Invalidated
                } else {
                    SessionRenewalResult.RetryableFailure(result.reason)
                }
            }
        }
    }

    fun discardSession() {
        clearInvalidSession()
    }

    private fun persistRenewedSession(
        response: PassengerSessionResponse,
        installationId: String,
    ): SessionRenewalResult = try {
        val renewedSession = response.toDomain(installationId)
        sessionStore.save(renewedSession)
        SessionRenewalResult.Renewed(renewedSession)
    } catch (_: Exception) {
        // El backend rota ambos tokens. Si el nuevo par no puede persistirse,
        // conservar el anterior dejaría una sesión aparentemente válida pero irrecuperable.
        clearInvalidSession()
        SessionRenewalResult.Invalidated
    }

    private fun clearInvalidSession() {
        runCatching(sessionStore::clear)
    }

    private fun PassengerSessionResponse.toDomain(installationId: String) = PassengerSession(
        accessToken = accessToken,
        accessTokenExpiresAt = Instant.parse(accessTokenExpiresAt),
        refreshToken = refreshToken,
        refreshTokenExpiresAt = Instant.parse(refreshTokenExpiresAt),
        installationId = installationId,
        user = PassengerSessionUser(
            publicId = user.publicId,
            email = user.email,
            firstName = user.firstName,
            lastName = user.lastName,
            status = user.status,
            locale = user.locale,
        ),
    )
}

sealed interface AuthenticationResult {
    data class Authenticated(val session: PassengerSession) : AuthenticationResult
    data class Failure(val reason: ApiFailure) : AuthenticationResult
    data object StorageFailure : AuthenticationResult
}

sealed interface PassengerRegistrationResult {
    data class Authenticated(val session: PassengerSession) : PassengerRegistrationResult
    data class VerificationRequired(val email: String) : PassengerRegistrationResult
    data class Failure(val reason: ApiFailure) : PassengerRegistrationResult
    data object StorageFailure : PassengerRegistrationResult
}

enum class LogoutResult {
    Completed,
    CompletedLocally,
    LocalStorageFailure,
}

sealed interface SessionRenewalResult {
    data class Renewed(val session: PassengerSession) : SessionRenewalResult
    data class RetryableFailure(val reason: ApiFailure) : SessionRenewalResult
    data object Invalidated : SessionRenewalResult
}
