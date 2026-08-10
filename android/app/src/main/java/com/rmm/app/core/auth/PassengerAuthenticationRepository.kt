package com.rmm.app.core.auth

import android.content.Context
import com.rmm.app.core.network.ApiFailure
import com.rmm.app.core.network.ApiResult
import com.rmm.app.core.network.RMMApiClient
import com.rmm.app.core.session.PassengerSession
import com.rmm.app.core.session.PassengerSessionStorage
import com.rmm.app.core.session.PassengerSessionUser
import java.time.Instant

class PassengerAuthenticationRepository(context: Context) {
    private val applicationContext = context.applicationContext
    private val api = RMMApiClient.create(PassengerAuthenticationApi::class.java)
    private val calls = RMMApiClient.calls()
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

enum class LogoutResult {
    Completed,
    CompletedLocally,
    LocalStorageFailure,
}
