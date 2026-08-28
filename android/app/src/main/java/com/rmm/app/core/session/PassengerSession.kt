package com.rmm.app.core.session

import java.time.Duration
import java.time.Instant
import java.util.UUID

data class PassengerSessionUser(
    val publicId: String,
    val email: String,
    val firstName: String,
    val lastName: String,
    val status: String,
    val locale: String,
)

data class PassengerSession(
    val accessToken: String,
    val accessTokenExpiresAt: Instant,
    val refreshToken: String,
    val refreshTokenExpiresAt: Instant,
    val installationId: String,
    val user: PassengerSessionUser,
) {
    init {
        require(accessToken.isNotBlank()) { "El token de acceso no puede estar vacio" }
        require(refreshToken.isNotBlank()) { "El token de renovacion no puede estar vacio" }
        require(installationId.isNotBlank()) { "La instalacion no puede estar vacia" }
        require(runCatching { UUID.fromString(installationId) }.isSuccess) {
            "La instalacion debe ser un UUID valido"
        }
        require(user.publicId.isNotBlank()) { "El identificador del pasajero no puede estar vacio" }
        require(user.email.isNotBlank()) { "El correo del pasajero no puede estar vacio" }
        require(!refreshTokenExpiresAt.isBefore(accessTokenExpiresAt)) {
            "La renovacion no puede caducar antes que el token de acceso"
        }
    }

    fun hasUsableAccessToken(
        now: Instant = Instant.now(),
        clockSkew: Duration = DEFAULT_CLOCK_SKEW,
    ): Boolean = accessTokenExpiresAt.isAfter(now.plus(clockSkew))

    fun canBeRefreshed(
        now: Instant = Instant.now(),
        clockSkew: Duration = DEFAULT_CLOCK_SKEW,
    ): Boolean = refreshTokenExpiresAt.isAfter(now.plus(clockSkew))

    private companion object {
        val DEFAULT_CLOCK_SKEW: Duration = Duration.ofSeconds(30)
    }
}
