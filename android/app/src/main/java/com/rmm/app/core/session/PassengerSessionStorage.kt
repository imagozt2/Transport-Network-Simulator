package com.rmm.app.core.session

import android.content.Context

object PassengerSessionStorage {
    @Volatile
    private var store: PassengerSessionStore? = null

    fun get(context: Context): PassengerSessionStore = store ?: synchronized(this) {
        store ?: SecurePassengerSessionStore(context.applicationContext).also { store = it }
    }
}
