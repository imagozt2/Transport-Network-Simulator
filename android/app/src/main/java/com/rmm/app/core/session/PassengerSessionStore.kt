package com.rmm.app.core.session

interface PassengerSessionStore {
    fun load(): PassengerSession?

    fun save(session: PassengerSession)

    fun clear()
}

class PassengerSessionStorageException(
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)
