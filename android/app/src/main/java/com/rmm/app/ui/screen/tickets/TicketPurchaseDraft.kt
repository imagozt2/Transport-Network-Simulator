package com.rmm.app.ui.screen.tickets

import com.rmm.app.core.ticketcatalog.PassengerTicketProduct
import com.rmm.app.core.ticketpurchase.PassengerTicketPurchaseConfiguration
import java.math.BigDecimal

internal data class TicketPurchaseDraft(
    val product: PassengerTicketProduct,
    val configuration: PassengerTicketPurchaseConfiguration,
    val totalAmount: BigDecimal,
    val originName: String? = null,
    val destinationName: String? = null,
)
