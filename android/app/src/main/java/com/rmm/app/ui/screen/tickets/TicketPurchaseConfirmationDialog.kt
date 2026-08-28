package com.rmm.app.ui.screen.tickets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.rmm.app.R
import com.rmm.app.core.network.ApiFailure
import com.rmm.app.core.network.ApiResult
import com.rmm.app.core.session.PassengerSession
import com.rmm.app.core.ticketpurchase.PassengerTicketPurchaseRepository
import com.rmm.app.core.ticketpurchase.PassengerTicketPurchaseRequest
import com.rmm.app.core.ticketpurchase.PassengerTicketPurchaseResponse
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

private sealed interface PurchaseConfirmationState {
    data object Ready : PurchaseConfirmationState
    data object Loading : PurchaseConfirmationState
    data class Success(val purchase: PassengerTicketPurchaseResponse) : PurchaseConfirmationState
    data class Error(val failure: ApiFailure) : PurchaseConfirmationState
}

@Composable
internal fun TicketPurchaseConfirmationDialog(
    session: PassengerSession,
    draft: TicketPurchaseDraft,
    onDismiss: () -> Unit,
) {
    val repository = remember { PassengerTicketPurchaseRepository() }
    val scope = rememberCoroutineScope()
    val idempotencyKey = remember(draft) { UUID.randomUUID().toString() }
    var state by remember(draft) { mutableStateOf<PurchaseConfirmationState>(PurchaseConfirmationState.Ready) }

    fun confirm() {
        scope.launch {
            state = PurchaseConfirmationState.Loading
            state = when (val result = repository.purchase(
                session = session,
                idempotencyKey = idempotencyKey,
                request = PassengerTicketPurchaseRequest(
                    productCode = draft.product.code,
                    configuration = draft.configuration,
                ),
            )) {
                is ApiResult.Success -> PurchaseConfirmationState.Success(result.value)
                is ApiResult.Failure -> PurchaseConfirmationState.Error(result.reason)
            }
        }
    }

    Dialog(onDismissRequest = {
        if (state !is PurchaseConfirmationState.Loading) onDismiss()
    }) {
        Surface(shape = MaterialTheme.shapes.large) {
            when (val current = state) {
                is PurchaseConfirmationState.Success -> PurchaseSuccess(current.purchase, draft, onDismiss)
                else -> PurchaseConfirmation(
                    draft = draft,
                    state = current,
                    onConfirm = ::confirm,
                    onDismiss = onDismiss,
                )
            }
        }
    }
}

@Composable
private fun PurchaseConfirmation(
    draft: TicketPurchaseDraft,
    state: PurchaseConfirmationState,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val loading = state is PurchaseConfirmationState.Loading
    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.ticket_purchase_confirmation_title),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            TextButton(onClick = onDismiss, enabled = !loading) {
                Text(stringResource(R.string.ticket_close_configuration))
            }
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(draft.product.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(draft.summary(), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(stringResource(R.string.ticket_purchase_simulated), fontWeight = FontWeight.Bold)
                Text(
                    stringResource(R.string.ticket_purchase_simulated_description),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.ticket_purchase_total), style = MaterialTheme.typography.titleMedium)
            Text(
                draft.totalAmount.money(draft.product.currency),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
        }
        if (state is PurchaseConfirmationState.Error) {
            Text(
                stringResource(R.string.ticket_purchase_error),
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (state !is PurchaseConfirmationState.Error) {
            Button(onClick = onConfirm, enabled = !loading, modifier = Modifier.fillMaxWidth()) {
                if (loading) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary)
                        Text(stringResource(R.string.ticket_purchase_processing))
                    }
                } else {
                    Text(stringResource(R.string.ticket_purchase_confirm))
                }
            }
        }
        if (state is PurchaseConfirmationState.Error) {
            OutlinedButton(onClick = onConfirm, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_retry))
            }
        }
    }
}

@Composable
private fun PurchaseSuccess(
    purchase: PassengerTicketPurchaseResponse,
    draft: TicketPurchaseDraft,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier.padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(R.string.ticket_purchase_success),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Text(
            stringResource(R.string.ticket_purchase_success_description),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        IssuedTicketCard(purchase, draft)
        Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.ticket_purchase_done))
        }
    }
}

@Composable
private fun IssuedTicketCard(
    purchase: PassengerTicketPurchaseResponse,
    draft: TicketPurchaseDraft,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Text(
                        draft.product.code.take(3),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        fontWeight = FontWeight.Bold,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.ticket_issued_title),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        draft.product.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text(
                        stringResource(R.string.ticket_issued_active),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Text(
                draft.summary(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            TicketDetailRow(stringResource(R.string.ticket_issued_identifier), purchase.ticketCode)
            TicketDetailRow(
                stringResource(R.string.ticket_issued_at),
                purchase.completedAt?.toDisplayDateTime().orEmpty(),
            )
            TicketDetailRow(
                stringResource(R.string.ticket_purchase_total),
                purchase.totalAmount.money(purchase.currency),
            )
            Text(
                stringResource(R.string.ticket_purchase_code, purchase.code),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TicketDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            label,
            modifier = Modifier.weight(0.4f),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            modifier = Modifier.weight(0.6f),
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun TicketPurchaseDraft.summary(): String = when (product.type) {
    "SINGLE_TRIP" -> stringResource(
        R.string.ticket_purchase_single_summary,
        originName.orEmpty(), destinationName.orEmpty(),
    )
    "MULTI_TRIP" -> stringResource(R.string.ticket_purchase_multi_summary, configuration.tripCount ?: 0)
    "TIME_PASS" -> stringResource(R.string.ticket_purchase_time_summary, configuration.dayCount ?: 0)
    "SMART_BALANCE" -> stringResource(
        R.string.ticket_purchase_balance_summary,
        configuration.rechargeAmount?.money(product.currency).orEmpty(),
    )
    else -> product.code
}

private fun BigDecimal.money(currencyCode: String): String = NumberFormat
    .getCurrencyInstance(Locale("es", "ES"))
    .apply { currency = Currency.getInstance(currencyCode) }
    .format(this)

private fun String.toDisplayDateTime(): String = replace('T', ' ').take(16)
