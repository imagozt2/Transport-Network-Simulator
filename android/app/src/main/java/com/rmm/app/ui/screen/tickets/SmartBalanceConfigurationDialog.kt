package com.rmm.app.ui.screen.tickets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.rmm.app.R
import com.rmm.app.core.ticketcatalog.PassengerTicketProduct
import com.rmm.app.core.ticketpurchase.PassengerTicketPurchaseConfiguration
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

@Composable
internal fun SmartBalanceConfigurationDialog(
    product: PassengerTicketProduct,
    onDismiss: () -> Unit,
    onConfigured: (TicketPurchaseDraft) -> Unit,
) {
    val minimum = product.minRechargeAmount ?: BigDecimal.ONE
    val maximum = product.maxRechargeAmount ?: minimum
    var amountText by rememberSaveable(product.code) { mutableStateOf(minimum.toPlainString()) }
    val amount = parseRechargeAmount(amountText)
    val valid = amount != null && amount >= minimum && amount <= maximum
    val presets = listOf(BigDecimal("5"), BigDecimal("10"), BigDecimal("20"), BigDecimal("50"))
        .filter { it >= minimum && it <= maximum }

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
                            stringResource(R.string.ticket_smart_balance_configuration),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.ticket_close_configuration))
                    }
                }

                Text(
                    stringResource(
                        R.string.ticket_choose_recharge_amount,
                        minimum.money(product.currency),
                        maximum.money(product.currency),
                    ),
                )

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { value ->
                        if (value.length <= 7 && value.matches(AMOUNT_INPUT_PATTERN)) {
                            amountText = value
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.ticket_recharge_amount)) },
                    suffix = { Text(product.currency) },
                    isError = amountText.isNotBlank() && !valid,
                    supportingText = if (!valid) {
                        { Text(stringResource(R.string.ticket_recharge_amount_error)) }
                    } else {
                        null
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )

                if (presets.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        presets.forEach { preset ->
                            FilterChip(
                                selected = amount?.compareTo(preset) == 0,
                                onClick = { amountText = preset.toPlainString() },
                                label = { Text(preset.money(product.currency)) },
                            )
                        }
                    }
                }

                if (valid && amount != null) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(stringResource(R.string.ticket_configuration_ready), style = MaterialTheme.typography.titleMedium)
                            Text(
                                amount.money(product.currency),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                stringResource(R.string.ticket_smart_balance_initial_balance),
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
                                            configuration = PassengerTicketPurchaseConfiguration(rechargeAmount = amount),
                                            totalAmount = amount,
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
}

internal fun parseRechargeAmount(value: String): BigDecimal? {
    val normalized = value.trim().replace(',', '.')
    if (normalized.isBlank()) return null
    return normalized.toBigDecimalOrNull()
        ?.takeIf { it.scale() <= 2 }
        ?.setScale(2, RoundingMode.UNNECESSARY)
}

private fun BigDecimal.money(currencyCode: String): String = NumberFormat
    .getCurrencyInstance(Locale("es", "ES"))
    .apply { currency = Currency.getInstance(currencyCode) }
    .format(this)

private val AMOUNT_INPUT_PATTERN = "^\\d{0,3}([.,]\\d{0,2})?$".toRegex()
