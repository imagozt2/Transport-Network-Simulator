package com.rmm.app.core.ticketwallet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhysicalTicketQrTest {

    @Test
    fun acceptsAndNormalizesAnRmmTicketQr() {
        assertEquals(
            "RMM:TICKET:1:header.payload.signature",
            parsePhysicalTicketQr("  RMM:TICKET:1:header.payload.signature  "),
        )
    }

    @Test
    fun rejectsEmptyForeignOversizedAndControlCharacterValues() {
        assertNull(parsePhysicalTicketQr(null))
        assertNull(parsePhysicalTicketQr(""))
        assertNull(parsePhysicalTicketQr("https://example.test/ticket"))
        assertNull(parsePhysicalTicketQr("RMM:TICKET:1:" + "a".repeat(4096)))
        assertNull(parsePhysicalTicketQr("RMM:TICKET:1:header\npayload"))
    }
}
