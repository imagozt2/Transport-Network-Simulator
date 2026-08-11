package com.rmm.app.ui.screen.tickets

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rmm.app.R
import com.rmm.app.core.network.ApiFailure
import com.rmm.app.core.network.ApiResult
import com.rmm.app.core.session.PassengerSession
import com.rmm.app.core.ticketwallet.PassengerTicketSummary
import com.rmm.app.core.ticketwallet.PassengerTicketWalletRepository
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale
import kotlinx.coroutines.launch

private data class WalletFilter(val status: String?, val label: Int)

private sealed interface TicketWalletUiState {
    data object Loading : TicketWalletUiState
    data class Content(
        val tickets: List<PassengerTicketSummary>,
        val nextCursor: String?,
        val loadingMore: Boolean = false,
        val paginationFailure: Boolean = false,
    ) : TicketWalletUiState
    data class Error(val failure: ApiFailure) : TicketWalletUiState
}

@Composable
internal fun TicketWallet(
    session: PassengerSession,
    modifier: Modifier = Modifier,
) {
    val repository = remember { PassengerTicketWalletRepository() }
    val scope = rememberCoroutineScope()
    var reloadKey by rememberSaveable { mutableIntStateOf(0) }
    var selectedStatus by rememberSaveable { mutableStateOf<String?>(null) }
    var state by remember { mutableStateOf<TicketWalletUiState>(TicketWalletUiState.Loading) }
    var qrTicketCode by remember { mutableStateOf<String?>(null) }
    var physicalQrValue by remember { mutableStateOf<String?>(null) }
    var scannerError by remember { mutableStateOf(false) }
    val filters = listOf(
        WalletFilter(null, R.string.ticket_wallet_filter_all),
        WalletFilter("ACTIVE", R.string.ticket_wallet_filter_active),
        WalletFilter("EXHAUSTED", R.string.ticket_wallet_filter_exhausted),
        WalletFilter("EXPIRED", R.string.ticket_wallet_filter_expired),
        WalletFilter("BLOCKED", R.string.ticket_wallet_filter_blocked),
        WalletFilter("CANCELLED", R.string.ticket_wallet_filter_cancelled),
    )

    LaunchedEffect(session.accessToken, selectedStatus, reloadKey) {
        state = TicketWalletUiState.Loading
        state = when (val result = repository.tickets(session, status = selectedStatus)) {
            is ApiResult.Success -> TicketWalletUiState.Content(
                result.value.items,
                result.value.nextCursor,
            )
            is ApiResult.Failure -> TicketWalletUiState.Error(result.reason)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        PhysicalTicketScannerButton(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            onScanned = { value ->
                scannerError = false
                physicalQrValue = value
            },
            onError = { scannerError = true },
        )
        if (scannerError) {
            Text(
                stringResource(R.string.ticket_link_scan_error),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.error,
            )
        }
        LazyRow(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(filters, key = { it.status ?: "ALL" }) { filter ->
                FilterChip(
                    selected = selectedStatus == filter.status,
                    onClick = { selectedStatus = filter.status },
                    label = { Text(stringResource(filter.label)) },
                )
            }
        }

        when (val current = state) {
            TicketWalletUiState.Loading -> WalletLoading()
            is TicketWalletUiState.Error -> WalletError(current.failure) { reloadKey++ }
            is TicketWalletUiState.Content -> WalletContent(
                state = current,
                onShowQr = { qrTicketCode = it },
                onLoadMore = {
                    val cursor = current.nextCursor ?: return@WalletContent
                    scope.launch {
                        state = current.copy(loadingMore = true, paginationFailure = false)
                        state = when (val result = repository.tickets(
                            session = session,
                            status = selectedStatus,
                            cursor = cursor,
                        )) {
                            is ApiResult.Success -> current.copy(
                                tickets = (current.tickets + result.value.items).distinctBy { it.code },
                                nextCursor = result.value.nextCursor,
                            )
                            is ApiResult.Failure -> current.copy(paginationFailure = true)
                        }
                    }
                },
            )
        }
    }

    qrTicketCode?.let { ticketCode ->
        TicketQrDialog(
            ticketCode = ticketCode,
            session = session,
            repository = repository,
            onDismiss = { qrTicketCode = null },
        )
    }
    physicalQrValue?.let { qrValue ->
        PhysicalTicketLinkDialog(
            qrValue = qrValue,
            session = session,
            repository = repository,
            onDismiss = { physicalQrValue = null },
            onLinked = {
                physicalQrValue = null
                selectedStatus = null
                reloadKey++
            },
        )
    }
}

@Composable
private fun WalletContent(
    state: TicketWalletUiState.Content,
    onShowQr: (String) -> Unit,
    onLoadMore: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (state.tickets.isEmpty()) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        stringResource(R.string.ticket_wallet_empty),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        stringResource(R.string.ticket_wallet_empty_description),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            items(state.tickets, key = PassengerTicketSummary::code) { ticket ->
                WalletTicketCard(ticket, onShowQr)
            }
        }
        state.nextCursor?.let {
            item {
                Button(
                    onClick = onLoadMore,
                    enabled = !state.loadingMore,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.loadingMore) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Text(stringResource(R.string.ticket_wallet_load_more))
                    }
                }
            }
        }
        if (state.paginationFailure) {
            item {
                Text(
                    stringResource(R.string.ticket_wallet_pagination_error),
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun WalletTicketCard(ticket: PassengerTicketSummary, onShowQr: (String) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.large),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text(
                        ticket.product.code.take(3),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        fontWeight = FontWeight.Bold,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        ticket.product.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        ticket.medium.mediumLabel(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TicketStatusBadge(ticket.status)
            }

            ticket.routeSummary()?.let { route ->
                Text(
                    route,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            TicketRightsDetails(ticket)
            HorizontalDivider()
            Text(
                ticket.statusDescription(),
                style = MaterialTheme.typography.bodyMedium,
                color = if (ticket.status == "ACTIVE") {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.error
                },
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    ticket.code,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    ticket.issuedAt.displayDate(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (ticket.openJourney) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text(
                        stringResource(R.string.ticket_wallet_open_journey),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            if (ticket.medium == "DIGITAL" && ticket.status == "ACTIVE") {
                Button(
                    onClick = { onShowQr(ticket.code) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.ticket_wallet_show_qr))
                }
            }
        }
    }
}

@Composable
private fun TicketStatusBadge(status: String) {
    val (containerColor, contentColor) = when (status) {
        "ACTIVE" -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        "BLOCKED", "CANCELLED" -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            status.statusLabel(),
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun TicketRightsDetails(ticket: PassengerTicketSummary) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (ticket.product.type) {
            "SINGLE_TRIP" -> TicketDataRow(
                stringResource(R.string.ticket_wallet_route_stations),
                pluralStringResource(
                    R.plurals.ticket_wallet_station_count,
                    ticket.stationCount ?: 0,
                    ticket.stationCount ?: 0,
                ),
            )
            "MULTI_TRIP" -> TicketDataRow(
                stringResource(R.string.ticket_wallet_trip_balance),
                pluralStringResource(
                    R.plurals.ticket_wallet_remaining_trips,
                    ticket.remainingTrips ?: 0,
                    ticket.remainingTrips ?: 0,
                ),
            )
            "TIME_PASS" -> {
                TicketDataRow(
                    stringResource(R.string.ticket_wallet_valid_from),
                    ticket.validFrom?.displayDateTime() ?: stringResource(R.string.ticket_wallet_not_available),
                )
                TicketDataRow(
                    stringResource(R.string.ticket_wallet_valid_until_label),
                    ticket.validUntil?.displayDateTime() ?: stringResource(R.string.ticket_wallet_not_available),
                )
            }
            "SMART_BALANCE" -> TicketDataRow(
                stringResource(R.string.ticket_wallet_money_balance),
                ticket.balanceAmount.money(ticket.currency),
            )
        }
    }
}

@Composable
private fun TicketDataRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun PassengerTicketSummary.routeSummary(): String? = when (product.type) {
    "SINGLE_TRIP" -> stringResource(
        R.string.ticket_wallet_single_trip,
        originStation?.name.orEmpty(),
        destinationStation?.name.orEmpty(),
    )
    else -> null
}

@Composable
private fun String?.mediumLabel(): String = stringResource(
    if (this == "PHYSICAL") R.string.ticket_wallet_physical else R.string.ticket_wallet_digital,
)

@Composable
private fun String.statusLabel(): String = stringResource(when (this) {
    "ACTIVE" -> R.string.ticket_status_active
    "EXHAUSTED" -> R.string.ticket_status_exhausted
    "EXPIRED" -> R.string.ticket_status_expired
    "BLOCKED" -> R.string.ticket_status_blocked
    "CANCELLED" -> R.string.ticket_status_cancelled
    else -> R.string.ticket_status_unknown
})

@Composable
private fun PassengerTicketSummary.statusDescription(): String = stringResource(when (status) {
    "ACTIVE" -> R.string.ticket_status_active_description
    "EXHAUSTED" -> R.string.ticket_status_exhausted_description
    "EXPIRED" -> R.string.ticket_status_expired_description
    "BLOCKED" -> R.string.ticket_status_blocked_description
    "CANCELLED" -> R.string.ticket_status_cancelled_description
    else -> R.string.ticket_status_unknown_description
})

private fun String.displayDate(): String = take(10).split('-').let { parts ->
    if (parts.size == 3) "${parts[2]}/${parts[1]}/${parts[0]}" else take(10)
}

private fun String.displayDateTime(): String {
    val date = displayDate()
    val time = substringAfter('T', "").take(5)
    return if (time.length == 5) "$date · $time" else date
}

private fun BigDecimal.money(currencyCode: String): String = NumberFormat
    .getCurrencyInstance(Locale("es", "ES"))
    .apply { currency = Currency.getInstance(currencyCode) }
    .format(this)

@Composable
private fun WalletLoading() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun WalletError(failure: ApiFailure, retry: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(
                    if (failure is ApiFailure.Network) R.string.ticket_wallet_network_error
                    else R.string.ticket_wallet_request_error,
                ),
            )
            Button(onClick = retry) { Text(stringResource(R.string.action_retry)) }
        }
    }
}
