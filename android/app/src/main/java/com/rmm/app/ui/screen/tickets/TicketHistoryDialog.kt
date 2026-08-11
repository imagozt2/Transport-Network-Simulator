package com.rmm.app.ui.screen.tickets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rmm.app.R
import com.rmm.app.core.network.ApiResult
import com.rmm.app.core.session.PassengerSession
import com.rmm.app.core.ticketwallet.PassengerTicketHistoryItem
import com.rmm.app.core.ticketwallet.PassengerTicketWalletRepository
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale
import kotlinx.coroutines.launch

private sealed interface TicketHistoryUiState {
    data object Loading : TicketHistoryUiState
    data object Error : TicketHistoryUiState
    data class Content(
        val items: List<PassengerTicketHistoryItem>,
        val nextCursor: String?,
        val loadingMore: Boolean = false,
        val paginationError: Boolean = false,
    ) : TicketHistoryUiState
}

@Composable
internal fun TicketHistoryDialog(
    ticketCode: String,
    session: PassengerSession,
    repository: PassengerTicketWalletRepository,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var retryKey by remember { mutableIntStateOf(0) }
    var state by remember(ticketCode) { mutableStateOf<TicketHistoryUiState>(TicketHistoryUiState.Loading) }

    LaunchedEffect(ticketCode, session.accessToken, retryKey) {
        state = TicketHistoryUiState.Loading
        state = when (val result = repository.ticketHistory(session, ticketCode)) {
            is ApiResult.Success -> TicketHistoryUiState.Content(result.value.items, result.value.nextCursor)
            is ApiResult.Failure -> TicketHistoryUiState.Error
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ticket_history_title)) },
        text = {
            when (val current = state) {
                TicketHistoryUiState.Loading -> Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) { CircularProgressIndicator() }
                TicketHistoryUiState.Error -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.ticket_history_error))
                    Button(onClick = { retryKey++ }) { Text(stringResource(R.string.action_retry)) }
                }
                is TicketHistoryUiState.Content -> LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (current.items.isEmpty()) item { Text(stringResource(R.string.ticket_history_empty)) }
                    items(current.items) { operation ->
                        TicketHistoryItemCard(operation)
                    }
                    current.nextCursor?.let { cursor ->
                        item {
                            Button(
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !current.loadingMore,
                                onClick = {
                                    scope.launch {
                                        state = current.copy(loadingMore = true, paginationError = false)
                                        state = when (val result = repository.ticketHistory(session, ticketCode, cursor)) {
                                            is ApiResult.Success -> current.copy(
                                                items = current.items + result.value.items,
                                                nextCursor = result.value.nextCursor,
                                            )
                                            is ApiResult.Failure -> current.copy(paginationError = true)
                                        }
                                    }
                                },
                            ) {
                                if (current.loadingMore) CircularProgressIndicator()
                                else Text(stringResource(R.string.ticket_history_load_more))
                            }
                        }
                    }
                    if (current.paginationError) item {
                        Text(stringResource(R.string.ticket_history_error), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.ticket_qr_close)) }
        },
    )
}

@Composable
private fun TicketHistoryItemCard(operation: PassengerTicketHistoryItem) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(operation.type.operationLabel(), fontWeight = FontWeight.Bold)
            Text(operation.occurredAt.historyDateTime(), color = MaterialTheme.colorScheme.onSurfaceVariant)
            operation.station?.let { Text(stringResource(R.string.ticket_history_at_station, it.name)) }
            operation.operationAmount?.takeIf { it.signum() != 0 }?.let {
                Text(stringResource(R.string.ticket_history_amount, it.money(operation.currency)))
            }
            operation.remainingTripsAfter?.let {
                Text(stringResource(R.string.ticket_history_remaining_trips, it))
            }
            operation.balanceAfter?.let {
                Text(stringResource(R.string.ticket_history_balance, it.money(operation.currency)))
            }
            operation.validUntilAfter?.let {
                Text(stringResource(R.string.ticket_history_valid_until, it.historyDateTime()))
            }
        }
    }
}

@Composable
private fun String.operationLabel(): String = stringResource(when (this) {
    "ISSUED" -> R.string.ticket_history_issued
    "RECHARGED" -> R.string.ticket_history_recharged
    "ENTRY_ACCEPTED" -> R.string.ticket_history_entry
    "EXIT_ACCEPTED" -> R.string.ticket_history_exit
    "BLOCKED" -> R.string.ticket_history_blocked
    "UNBLOCKED" -> R.string.ticket_history_unblocked
    "CANCELLED" -> R.string.ticket_history_cancelled
    "SUPPORT_LINKED" -> R.string.ticket_history_linked
    "QR_REVOKED" -> R.string.ticket_history_qr_revoked
    else -> R.string.ticket_history_unknown
})

private fun String.historyDateTime(): String {
    val date = take(10).split('-').let { if (it.size == 3) "${it[2]}/${it[1]}/${it[0]}" else take(10) }
    val time = substringAfter('T', "").take(5)
    return if (time.length == 5) "$date · $time" else date
}

private fun BigDecimal.money(currencyCode: String): String = NumberFormat
    .getCurrencyInstance(Locale("es", "ES"))
    .apply { currency = Currency.getInstance(currencyCode) }
    .format(this)
