package com.rmm.app.core.environment

enum class RMMEnvironment {
    LOCAL,
    STAGING,
    PRODUCTION;

    companion object {
        fun from(value: String): RMMEnvironment = entries.firstOrNull {
            it.name.equals(value.trim(), ignoreCase = true)
        } ?: throw IllegalArgumentException("Unknown RMM environment: $value")
    }
}
