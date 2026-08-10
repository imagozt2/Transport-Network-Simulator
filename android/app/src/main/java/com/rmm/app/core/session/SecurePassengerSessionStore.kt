package com.rmm.app.core.session

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import java.nio.charset.StandardCharsets
import java.time.Instant

class SecurePassengerSessionStore private constructor(
    private val preferences: SharedPreferences,
    private val cipher: SessionCipher,
    private val gson: Gson,
) : PassengerSessionStore {
    constructor(context: Context) : this(
        preferences = context.applicationContext.getSharedPreferences(
            PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        ),
        cipher = AndroidKeystoreSessionCipher(),
        gson = Gson(),
    )

    private val lock = Any()

    override fun load(): PassengerSession? = synchronized(lock) {
        val encryptedSession = preferences.getString(SESSION_KEY, null) ?: return@synchronized null

        try {
            val json = cipher.decrypt(encryptedSession).toString(StandardCharsets.UTF_8)
            gson.fromJson(json, StoredPassengerSession::class.java).toDomain()
        } catch (_: Exception) {
            removeUnreadableSession()
            null
        }
    }

    override fun save(session: PassengerSession) = synchronized(lock) {
        try {
            val json = gson.toJson(StoredPassengerSession.from(session))
            val encryptedSession = cipher.encrypt(json.toByteArray(StandardCharsets.UTF_8))
            if (!preferences.edit().putString(SESSION_KEY, encryptedSession).commit()) {
                throw PassengerSessionStorageException("No se ha podido guardar la sesion")
            }
        } catch (exception: PassengerSessionStorageException) {
            throw exception
        } catch (exception: Exception) {
            throw PassengerSessionStorageException(
                "No se ha podido cifrar y guardar la sesion",
                exception,
            )
        }
    }

    override fun clear() = synchronized(lock) {
        if (!preferences.edit().remove(SESSION_KEY).commit()) {
            throw PassengerSessionStorageException("No se ha podido eliminar la sesion")
        }
    }

    private fun removeUnreadableSession() {
        preferences.edit().remove(SESSION_KEY).commit()
        runCatching(cipher::resetKey)
    }

    private data class StoredPassengerSession(
        val schemaVersion: Int,
        val accessToken: String,
        val accessTokenExpiresAt: String,
        val refreshToken: String,
        val refreshTokenExpiresAt: String,
        val installationId: String,
        val user: StoredPassengerSessionUser,
    ) {
        fun toDomain(): PassengerSession {
            require(schemaVersion == CURRENT_SCHEMA_VERSION) {
                "Version de sesion no compatible"
            }
            return PassengerSession(
                accessToken = accessToken,
                accessTokenExpiresAt = Instant.parse(accessTokenExpiresAt),
                refreshToken = refreshToken,
                refreshTokenExpiresAt = Instant.parse(refreshTokenExpiresAt),
                installationId = installationId,
                user = user.toDomain(),
            )
        }

        companion object {
            fun from(session: PassengerSession) = StoredPassengerSession(
                schemaVersion = CURRENT_SCHEMA_VERSION,
                accessToken = session.accessToken,
                accessTokenExpiresAt = session.accessTokenExpiresAt.toString(),
                refreshToken = session.refreshToken,
                refreshTokenExpiresAt = session.refreshTokenExpiresAt.toString(),
                installationId = session.installationId,
                user = StoredPassengerSessionUser.from(session.user),
            )
        }
    }

    private data class StoredPassengerSessionUser(
        val publicId: String,
        val email: String,
        val firstName: String,
        val lastName: String,
        val status: String,
        val locale: String,
    ) {
        fun toDomain() = PassengerSessionUser(
            publicId = publicId,
            email = email,
            firstName = firstName,
            lastName = lastName,
            status = status,
            locale = locale,
        )

        companion object {
            fun from(user: PassengerSessionUser) = StoredPassengerSessionUser(
                publicId = user.publicId,
                email = user.email,
                firstName = user.firstName,
                lastName = user.lastName,
                status = user.status,
                locale = user.locale,
            )
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "rmm_secure_passenger_session"
        const val SESSION_KEY = "encrypted_session"
        const val CURRENT_SCHEMA_VERSION = 1
    }
}
