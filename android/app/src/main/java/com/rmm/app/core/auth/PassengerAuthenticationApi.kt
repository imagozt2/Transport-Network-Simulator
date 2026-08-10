package com.rmm.app.core.auth

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface PassengerAuthenticationApi {
    @POST("auth/register")
    suspend fun register(
        @Body request: PassengerRegistrationRequest,
    ): Response<PassengerRegistrationResponse>

    @POST("auth/sessions")
    suspend fun login(
        @Body request: PassengerLoginRequest,
    ): Response<PassengerSessionResponse>
}

data class PassengerRegistrationRequest(
    val email: String,
    val password: String,
    val firstName: String,
    val lastName: String,
    val locale: String = "es-ES",
    val termsVersion: String = "2026-01",
)

data class PassengerLoginRequest(
    val email: String,
    val password: String,
    val device: PassengerDeviceRequest,
)

data class PassengerDeviceRequest(
    val installationId: String,
    val name: String,
    val platform: String = "ANDROID",
)

data class PassengerRegistrationResponse(
    val user: PassengerUserResponse,
    val verificationRequired: Boolean,
)

data class PassengerSessionResponse(
    val accessToken: String,
    val accessTokenExpiresAt: String,
    val refreshToken: String,
    val refreshTokenExpiresAt: String,
    val user: PassengerUserResponse,
)

data class PassengerUserResponse(
    val publicId: String,
    val email: String,
    val firstName: String,
    val lastName: String,
    val status: String,
    val locale: String,
)
