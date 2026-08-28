package com.rmm.app.ui.screen.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import com.rmm.app.core.network.ApiFailure
import com.rmm.app.core.network.ApiResult
import com.rmm.app.core.session.PassengerSession
import com.rmm.app.core.ticketwallet.PassengerTicketSummary
import com.rmm.app.core.ticketwallet.PassengerTicketWalletRepository

private sealed interface HomeUiState {
    data object Loading : HomeUiState
    data class Content(val openJourneys: List<PassengerTicketSummary>) : HomeUiState
    data class Error(val failure: ApiFailure) : HomeUiState
}

@Composable
fun HomeScreen(
    session: PassengerSession,
    modifier: Modifier = Modifier,
) {
    val repository = remember { PassengerTicketWalletRepository() }
    var reloadKey by rememberSaveable { mutableIntStateOf(0) }
    var state by remember { mutableStateOf<HomeUiState>(HomeUiState.Loading) }

    LaunchedEffect(session.accessToken, reloadKey) {
        state = HomeUiState.Loading
        state = when (val result = repository.openDigitalJourneys(session)) {
            is ApiResult.Success -> HomeUiState.Content(result.value)
            is ApiResult.Failure -> HomeUiState.Error(result.reason)
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.home_journeys_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(R.string.home_journeys_description),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        when (val current = state) {
            HomeUiState.Loading -> item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator()
                }
            }
            is HomeUiState.Error -> item {
                HomeStatusCard {
                    Text(
                        text = stringResource(R.string.home_journeys_error),
                        color = MaterialTheme.colorScheme.error,
                    )
                    Button(onClick = { reloadKey++ }) {
                        Text(stringResource(R.string.home_journeys_retry))
                    }
                }
            }
            is HomeUiState.Content -> {
                if (current.openJourneys.isEmpty()) {
                    item {
                        HomeStatusCard {
                            Text(
                                text = stringResource(R.string.home_journeys_none),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = stringResource(R.string.home_journeys_none_description),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    item {
                        Text(
                            text = stringResource(
                                R.string.home_journeys_open_count,
                                current.openJourneys.size,
                            ),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    items(current.openJourneys, key = PassengerTicketSummary::code) { ticket ->
                        OpenJourneyCard(ticket)
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeStatusCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

@Composable
private fun OpenJourneyCard(ticket: PassengerTicketSummary) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = ticket.product.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(R.string.home_journeys_open_badge),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                text = stringResource(R.string.home_journeys_ticket, ticket.code),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
