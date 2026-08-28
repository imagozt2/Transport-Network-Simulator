package com.rmm.app.ui.screen.journeys

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rmm.app.R
import com.rmm.app.core.journeyhistory.PassengerJourneyHistoryItem
import com.rmm.app.core.journeyhistory.PassengerJourneyHistoryRepository
import com.rmm.app.core.network.ApiResult
import com.rmm.app.core.session.PassengerSession
import java.text.NumberFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Currency
import java.util.Locale

private sealed interface JourneyHistoryUiState {
    data object Loading : JourneyHistoryUiState
    data class Content(
        val items: List<PassengerJourneyHistoryItem>,
        val nextCursor: String?,
        val loadingMore: Boolean = false,
        val paginationFailed: Boolean = false,
    ) : JourneyHistoryUiState
    data object Error : JourneyHistoryUiState
}

@Composable
fun JourneyHistorySection(session: PassengerSession, modifier: Modifier = Modifier) {
    val repository = remember { PassengerJourneyHistoryRepository() }
    var reloadKey by rememberSaveable { mutableIntStateOf(0) }
    var state by remember { mutableStateOf<JourneyHistoryUiState>(JourneyHistoryUiState.Loading) }
    var selectedJourney by remember { mutableStateOf<PassengerJourneyHistoryItem?>(null) }

    LaunchedEffect(session.accessToken, reloadKey) {
        state = JourneyHistoryUiState.Loading
        state = when (val result = repository.history(session)) {
            is ApiResult.Success -> JourneyHistoryUiState.Content(
                result.value.items, result.value.nextCursor,
            )
            is ApiResult.Failure -> JourneyHistoryUiState.Error
        }
    }

    when (val current = state) {
        JourneyHistoryUiState.Loading -> LoadingState()
        JourneyHistoryUiState.Error -> ErrorState(com.rmm.app.core.network.ApiFailure.Unexpected) {
            reloadKey++
        }
        is JourneyHistoryUiState.Content -> {
            if (current.items.isEmpty()) {
                EmptyState(R.string.journey_history_empty)
            } else {
                LazyColumn(
                    modifier = modifier,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(current.items, key = PassengerJourneyHistoryItem::code) {
                        JourneyHistoryCard(it, onOpenDetail = { selectedJourney = it })
                    }
                    current.nextCursor?.let { cursor ->
                        item {
                            Button(
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !current.loadingMore,
                                onClick = {
                                    state = current.copy(loadingMore = true, paginationFailed = false)
                                },
                            ) {
                                if (current.loadingMore) CircularProgressIndicator()
                                else Text(stringResource(R.string.journey_history_load_more))
                            }
                            if (current.paginationFailed) {
                                Text(
                                    stringResource(R.string.journey_history_error),
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                        if (current.loadingMore) {
                            item {
                                LaunchedEffect(cursor) {
                                    state = when (val result = repository.history(session, cursor)) {
                                        is ApiResult.Success -> current.copy(
                                            items = current.items + result.value.items,
                                            nextCursor = result.value.nextCursor,
                                            loadingMore = false,
                                        )
                                        is ApiResult.Failure -> current.copy(
                                            loadingMore = false,
                                            paginationFailed = true,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    selectedJourney?.let { journey ->
        JourneyDetailDialog(journey = journey, onDismiss = { selectedJourney = null })
    }
}

@Composable
private fun JourneyHistoryCard(
    journey: PassengerJourneyHistoryItem,
    onOpenDetail: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth(), onClick = onOpenDetail) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(journey.origin.name, style = MaterialTheme.typography.titleMedium)
                JourneyStatus(journey)
            }
            Text(
                journey.destination?.let {
                    stringResource(R.string.journey_history_destination, it.name)
                } ?: stringResource(R.string.journey_history_without_exit),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                journey.openedAt.asJourneyDateTime(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                journey.stationCount?.let {
                    Text(stringResource(R.string.journey_history_stations, it))
                }
                journey.durationSeconds?.let {
                    Text(stringResource(R.string.journey_history_duration, it / 60, it % 60))
                }
                journey.fareAmount?.let {
                    val amount = NumberFormat.getCurrencyInstance(Locale("es", "ES")).apply {
                        currency = Currency.getInstance(journey.currency)
                    }.format(it)
                    Text(stringResource(R.string.journey_history_fare, amount))
                }
            }
            Text(
                stringResource(R.string.journey_history_ticket, journey.productName),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(R.string.journey_detail_open),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun JourneyStatus(journey: PassengerJourneyHistoryItem) {
    val label = when (journey.status) {
        "OPEN" -> R.string.journey_history_status_open
        "CLOSED" -> R.string.journey_history_status_closed
        else -> R.string.journey_history_status_anomaly
    }
    Surface(
        color = if (journey.anomalous) MaterialTheme.colorScheme.errorContainer
        else MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small,
    ) {
        Text(stringResource(label), modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp))
    }
}

internal fun String.asJourneyDateTime(): String = runCatching {
    LocalDateTime.parse(this).format(DateTimeFormatter.ofPattern("dd/MM/yyyy · HH:mm"))
}.getOrDefault(this)
