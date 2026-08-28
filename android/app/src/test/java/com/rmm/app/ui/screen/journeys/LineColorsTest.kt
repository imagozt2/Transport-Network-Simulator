package com.rmm.app.ui.screen.journeys

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class LineColorsTest {

    @Test
    fun resolvesDatabaseColorNamesAndFallsBackToTheLineCode() {
        assertEquals(Color(0xFFD32F2F), resolvedLineColor("L1", "Roja", Color.Black))
        assertEquals(Color(0xFF2E9445), resolvedLineColor("L2", "Verde", Color.Black))
        assertEquals(Color(0xFFFFB71B), resolvedLineColor("L3", null, Color.Black))
        assertEquals(Color(0xFF7B1FA2), resolvedLineColor("L4", "Morada", Color.Black))
        assertEquals(Color(0xFF1976D2), resolvedLineColor("L5", "Azul", Color.Black))
        assertEquals(Color(0xFFF57C00), resolvedLineColor("L6", "Naranja", Color.Black))
    }
}
