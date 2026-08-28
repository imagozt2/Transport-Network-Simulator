package com.rmm.app.ui.screen.journeys

import androidx.compose.ui.graphics.Color

internal fun resolvedLineColor(code: String, configured: String?, fallback: Color): Color {
    val named = when (configured?.trim()?.lowercase()) {
        "roja", "rojo" -> Color(0xFFD32F2F)
        "verde" -> Color(0xFF2E9445)
        "amarilla", "amarillo" -> Color(0xFFFFB71B)
        "morada", "morado" -> Color(0xFF7B1FA2)
        "azul" -> Color(0xFF1976D2)
        "naranja" -> Color(0xFFF57C00)
        else -> null
    }
    if (named != null) return named

    val hexadecimal = try {
        configured?.takeIf { it.isNotBlank() }?.let {
            Color(android.graphics.Color.parseColor(it))
        }
    } catch (_: IllegalArgumentException) {
        null
    }
    if (hexadecimal != null) return hexadecimal

    return when (code.uppercase()) {
        "L1" -> Color(0xFFD32F2F)
        "L2" -> Color(0xFF2E9445)
        "L3" -> Color(0xFFFFB71B)
        "L4" -> Color(0xFF7B1FA2)
        "L5" -> Color(0xFF1976D2)
        "L6" -> Color(0xFFF57C00)
        else -> fallback
    }
}
