package com.rmm.app.ui.screen.tickets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.rmm.app.R
import com.rmm.app.core.ticketcatalog.PassengerTicketProduct
import com.rmm.app.core.ticketpurchase.PassengerTicketPurchaseConfiguration
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

@Composable
internal fun MultiTripConfigurationDialog(
    product: PassengerTicketProduct,
    onDismiss: () -> Unit,
    onConfigured: (TicketPurchaseDraft) -> Unit,
) {
    val minimum = product.minTrips ?: 1
    val maximum = product.maxTrips ?: minimum
    var tripCount by rememberSaveable(product.code) { mutableIntStateOf(minimum) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(product.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text(
                            stringResource(R.string.ticket_multi_trip_configuration),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.ticket_close_configuration))
                    }
                }

                Text(
                    stringResource(R.string.ticket_choose_trip_count, minimum, maximum),
                    style = MaterialTheme.typography.bodyLarge,
                )

                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(18.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FilledTonalButton(
                            onClick = { tripCount-- },
                            enabled = tripCount > minimum,
                        ) {
                            Text("−", style = MaterialTheme.typography.titleLarge)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                tripCount.toString(),
                                style = MaterialTheme.typography.displaySmall,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(stringResource(R.string.ticket_trips), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        FilledTonalButton(
                            onClick = { tripCount++ },
                            enabled = tripCount < maximum,
                        ) {
                            Text("+", style = MaterialTheme.typography.titleLarge)
                        }
                    }
                }

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(stringResource(R.string.ticket_configuration_ready), style = MaterialTheme.typography.titleMedium)
                        Text(
                            (product.pricePerTrip * BigDecimal.valueOf(tripCount.toLong())).money(product.currency),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            stringResource(R.string.ticket_multi_trip_price_detail, product.pricePerTrip.money(product.currency), tripCount),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            stringResource(R.string.ticket_estimated_price_notice),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        androidx.compose.material3.Button(
                            onClick = {
                                onConfigured(
                                    TicketPurchaseDraft(
                                        product = product,
                                        configuration = PassengerTicketPurchaseConfiguration(tripCount = tripCount),
                                        totalAmount = product.pricePerTrip * BigDecimal.valueOf(tripCount.toLong()),
                                    ),
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.ticket_continue_purchase))
                        }
                    }
                }
            }
        }
    }
}

private fun BigDecimal.money(currencyCode: String): String = NumberFormat
    .getCurrencyInstance(Locale("es", "ES"))
    .apply { currency = Currency.getInstance(currencyCode) }
    .format(this)
