package com.rmm.app.core.journeys

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class SavedPassengerJourney(
    val originCode: String,
    val originName: String,
    val destinationCode: String,
    val destinationName: String,
    val savedAtEpochMillis: Long,
) {
    val routeKey: String get() = "$originCode>$destinationCode"
}

data class PassengerJourneyHistory(
    val recent: List<SavedPassengerJourney> = emptyList(),
    val favorites: List<SavedPassengerJourney> = emptyList(),
)

interface PassengerJourneyHistoryStore {
    fun load(): PassengerJourneyHistory
    fun record(journey: SavedPassengerJourney): PassengerJourneyHistory
    fun toggleFavorite(journey: SavedPassengerJourney): PassengerJourneyHistory
}

class SharedPreferencesPassengerJourneyHistoryStore(
    context: Context,
    passengerPublicId: String,
    private val gson: Gson = Gson(),
) : PassengerJourneyHistoryStore {
    private val preferences: SharedPreferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )
    private val storageKey = "journeys_$passengerPublicId"
    private val lock = Any()

    override fun load(): PassengerJourneyHistory = synchronized(lock) {
        val json = preferences.getString(storageKey, null) ?: return@synchronized PassengerJourneyHistory()
        runCatching {
            gson.fromJson<StoredHistory>(json, object : TypeToken<StoredHistory>() {}.type).toDomain()
        }.getOrElse {
            preferences.edit().remove(storageKey).apply()
            PassengerJourneyHistory()
        }
    }

    override fun record(journey: SavedPassengerJourney): PassengerJourneyHistory = synchronized(lock) {
        val current = load()
        save(
            current.copy(
                recent = listOf(journey) + current.recent
                    .filterNot { it.routeKey == journey.routeKey }
                    .take(MAX_RECENT - 1),
            ),
        )
    }

    override fun toggleFavorite(journey: SavedPassengerJourney): PassengerJourneyHistory = synchronized(lock) {
        val current = load()
        val alreadyFavorite = current.favorites.any { it.routeKey == journey.routeKey }
        save(
            current.copy(
                favorites = if (alreadyFavorite) {
                    current.favorites.filterNot { it.routeKey == journey.routeKey }
                } else {
                    listOf(journey) + current.favorites
                        .filterNot { it.routeKey == journey.routeKey }
                        .take(MAX_FAVORITES - 1)
                },
            ),
        )
    }

    private fun save(history: PassengerJourneyHistory): PassengerJourneyHistory {
        val stored = StoredHistory.from(history)
        check(preferences.edit().putString(storageKey, gson.toJson(stored)).commit()) {
            "No se ha podido guardar el historial de trayectos"
        }
        return history
    }

    private data class StoredHistory(
        val schemaVersion: Int,
        val recent: List<SavedPassengerJourney>,
        val favorites: List<SavedPassengerJourney>,
    ) {
        fun toDomain(): PassengerJourneyHistory {
            require(schemaVersion == CURRENT_SCHEMA_VERSION)
            return PassengerJourneyHistory(recent.take(MAX_RECENT), favorites.take(MAX_FAVORITES))
        }

        companion object {
            fun from(history: PassengerJourneyHistory) = StoredHistory(
                schemaVersion = CURRENT_SCHEMA_VERSION,
                recent = history.recent,
                favorites = history.favorites,
            )
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "rmm_passenger_journey_history"
        const val CURRENT_SCHEMA_VERSION = 1
        const val MAX_RECENT = 10
        const val MAX_FAVORITES = 20
    }
}
