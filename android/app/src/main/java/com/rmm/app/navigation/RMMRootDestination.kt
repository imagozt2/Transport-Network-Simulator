package com.rmm.app.navigation

import com.rmm.app.core.session.PassengerSession

enum class RMMRootDestination {
    AUTHENTICATION,
    APPLICATION,
}

fun resolveRootDestination(session: PassengerSession?): RMMRootDestination =
    if (session == null) {
        RMMRootDestination.AUTHENTICATION
    } else {
        RMMRootDestination.APPLICATION
    }
