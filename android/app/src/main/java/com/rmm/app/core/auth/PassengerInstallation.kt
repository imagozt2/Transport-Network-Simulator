package com.rmm.app.core.auth

import android.content.Context
import android.os.Build
import java.util.UUID

class PassengerInstallation(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    val id: String
        get() = preferences.getString(INSTALLATION_ID_KEY, null)
            ?: UUID.randomUUID().toString().also { generatedId ->
                check(preferences.edit().putString(INSTALLATION_ID_KEY, generatedId).commit()) {
                    "No se ha podido guardar el identificador de la instalacion"
                }
            }

    val deviceName: String
        get() = listOf(Build.MANUFACTURER, Build.MODEL)
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinctBy(String::lowercase)
            .joinToString(" ")
            .take(MAXIMUM_DEVICE_NAME_LENGTH)
            .ifBlank { "Dispositivo Android" }

    private companion object {
        const val PREFERENCES_NAME = "rmm_passenger_installation"
        const val INSTALLATION_ID_KEY = "installation_id"
        const val MAXIMUM_DEVICE_NAME_LENGTH = 100
    }
}
