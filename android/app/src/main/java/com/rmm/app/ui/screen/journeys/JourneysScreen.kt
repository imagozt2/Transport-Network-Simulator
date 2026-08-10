package com.rmm.app.ui.screen.journeys

import android.graphics.Color.parseColor
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rmm.app.R
import com.rmm.app.core.network.ApiFailure
import com.rmm.app.core.networkcatalog.NetworkCatalog
import com.rmm.app.core.networkcatalog.NetworkCatalogResult
import com.rmm.app.core.networkcatalog.PassengerNetworkLine
import com.rmm.app.core.networkcatalog.PassengerNetworkRepository
import com.rmm.app.core.networkcatalog.PassengerNetworkStation
import com.rmm.app.core.session.PassengerSession

private enum class CatalogTab { LINES, STATIONS }

private sealed interface CatalogUiState {
    data object Loading : CatalogUiState
    data class Content(val catalog: NetworkCatalog) : CatalogUiState
    data class Error(val failure: ApiFailure) : CatalogUiState
}

@Composable
fun JourneysScreen(
    session: PassengerSession,
    modifier: Modifier = Modifier,
) {
    val repository = remember { PassengerNetworkRepository() }
    var reloadKey by rememberSaveable { mutableIntStateOf(0) }
    var selectedTab by rememberSaveable { mutableStateOf(CatalogTab.LINES) }
    var state by remember { mutableStateOf<CatalogUiState>(CatalogUiState.Loading) }

    LaunchedEffect(session.accessToken, reloadKey) {
        state = CatalogUiState.Loading
        state = when (val result = repository.catalog(session)) {
            is NetworkCatalogResult.Success -> CatalogUiState.Content(result.catalog)
            is NetworkCatalogResult.Failure -> CatalogUiState.Error(result.reason)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text(stringResource(R.string.journeys_network_title), style = MaterialTheme.typography.headlineMedium)
            Text(
                stringResource(R.string.journeys_network_description),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            TabRow(selectedTabIndex = selectedTab.ordinal) {
                Tab(
                    selected = selectedTab == CatalogTab.LINES,
                    onClick = { selectedTab = CatalogTab.LINES },
                    text = { Text(stringResource(R.string.journeys_lines_tab)) },
                )
                Tab(
                    selected = selectedTab == CatalogTab.STATIONS,
                    onClick = { selectedTab = CatalogTab.STATIONS },
                    text = { Text(stringResource(R.string.journeys_stations_tab)) },
                )
            }
        }

        when (val current = state) {
            CatalogUiState.Loading -> LoadingState()
            is CatalogUiState.Error -> ErrorState(current.failure) { reloadKey++ }
            is CatalogUiState.Content -> when (selectedTab) {
                CatalogTab.LINES -> LinesList(current.catalog)
                CatalogTab.STATIONS -> StationsList(current.catalog)
            }
        }
    }
}

@Composable
private fun LoadingState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorState(failure: ApiFailure, retry: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = when (failure) {
                    is ApiFailure.Network -> stringResource(R.string.journeys_network_offline)
                    is ApiFailure.Http -> stringResource(R.string.journeys_network_http_error)
                    else -> stringResource(R.string.journeys_network_unknown_error)
                },
                style = MaterialTheme.typography.bodyLarge,
            )
            Button(onClick = retry) { Text(stringResource(R.string.action_retry)) }
        }
    }
}

@Composable
private fun LinesList(catalog: NetworkCatalog) {
    if (catalog.lines.isEmpty()) {
        EmptyState(R.string.journeys_no_lines)
        return
    }
    val stationsByCode = remember(catalog.stations) { catalog.stations.associateBy { it.code } }
    LazyColumn(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(catalog.lines, key = PassengerNetworkLine::code) { line ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LineBadge(line.code, line.color)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(line.name, style = MaterialTheme.typography.titleMedium)
                        val terminalNames = line.terminals.map { code ->
                            stationsByCode[code]?.name ?: code
                        }
                        Text(
                            terminalNames.joinToString(" — ").ifBlank {
                                stringResource(R.string.journeys_terminals_unavailable)
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        val stationCount = catalog.stations.count { line.code in it.lineCodes }
                        Text(
                            stringResource(R.string.journeys_station_count, stationCount),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StationsList(catalog: NetworkCatalog) {
    var query by rememberSaveable { mutableStateOf("") }
    val filteredStations = remember(catalog.stations, query) {
        catalog.stations.filter { station ->
            query.isBlank()
                || station.name.contains(query.trim(), ignoreCase = true)
                || station.code.contains(query.trim(), ignoreCase = true)
        }
    }
    val lineColors = remember(catalog.lines) { catalog.lines.associate { it.code to it.color } }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text(stringResource(R.string.journeys_search_station)) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
        )
        Spacer(Modifier.height(8.dp))
        if (filteredStations.isEmpty()) {
            EmptyState(R.string.journeys_no_stations)
        } else {
            LazyColumn(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(filteredStations, key = PassengerNetworkStation::code) { station ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(station.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    station.code,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                station.lineCodes.forEach { lineCode ->
                                    LineBadge(lineCode, lineColors[lineCode])
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LineBadge(code: String, color: String?) {
    val background = color.toColorOr(MaterialTheme.colorScheme.primary)
    Box(
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .background(background)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = code,
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun EmptyState(message: Int) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            stringResource(message),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun String?.toColorOr(fallback: Color): Color = try {
    if (isNullOrBlank()) fallback else Color(parseColor(this))
} catch (_: IllegalArgumentException) {
    fallback
}
