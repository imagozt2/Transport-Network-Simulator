package com.rmm.app.core.ticketwallet

private const val RMM_TICKET_QR_PREFIX = "RMM:TICKET:"
private const val MAXIMUM_TICKET_QR_LENGTH = 4096

internal fun parsePhysicalTicketQr(rawValue: String?): String? = rawValue
    ?.trim()
    ?.takeIf { value ->
        value.startsWith(RMM_TICKET_QR_PREFIX) &&
            value.length <= MAXIMUM_TICKET_QR_LENGTH &&
            value.none(Char::isISOControl)
    }
