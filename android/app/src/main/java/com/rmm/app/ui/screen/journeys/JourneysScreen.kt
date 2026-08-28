package com.rmm.app.ui.screen.journeys

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.ScrollableTabRow
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
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
import com.rmm.app.core.session.PassengerSession

private enum class CatalogTab { ROUTE, MAP, LINES, HISTORY }

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
    var selectedTab by rememberSaveable { mutableStateOf(CatalogTab.MAP) }
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
            ScrollableTabRow(selectedTabIndex = selectedTab.ordinal, edgePadding = 0.dp) {
                Tab(
                    selected = selectedTab == CatalogTab.ROUTE,
                    onClick = { selectedTab = CatalogTab.ROUTE },
                    text = { Text(stringResource(R.string.journeys_route_tab)) },
                )
                Tab(
                    selected = selectedTab == CatalogTab.MAP,
                    onClick = { selectedTab = CatalogTab.MAP },
                    text = { Text(stringResource(R.string.journeys_map_tab)) },
                )
                Tab(
                    selected = selectedTab == CatalogTab.LINES,
                    onClick = { selectedTab = CatalogTab.LINES },
                    text = { Text(stringResource(R.string.journeys_lines_tab)) },
                )
                Tab(
                    selected = selectedTab == CatalogTab.HISTORY,
                    onClick = { selectedTab = CatalogTab.HISTORY },
                    text = { Text(stringResource(R.string.journey_history_tab)) },
                )
            }
        }

        if (selectedTab == CatalogTab.HISTORY) {
            JourneyHistorySection(session = session, modifier = Modifier.fillMaxSize())
        } else when (val current = state) {
            CatalogUiState.Loading -> LoadingState()
            is CatalogUiState.Error -> ErrorState(current.failure) { reloadKey++ }
            is CatalogUiState.Content -> when (selectedTab) {
                CatalogTab.MAP -> NetworkMapView(current.catalog)
                CatalogTab.ROUTE -> JourneyPlanner(
                    session = session,
                    catalog = current.catalog,
                    repository = repository,
                )
                CatalogTab.LINES -> LinesList(current.catalog)
                CatalogTab.HISTORY -> Unit
            }
        }
    }
}

@Composable
internal fun LoadingState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
internal fun ErrorState(failure: ApiFailure, retry: () -> Unit) {
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
    val linesByCode = remember(catalog.lines) { catalog.lines.associateBy { it.code } }
    var expandedLineCode by rememberSaveable { mutableStateOf<String?>(null) }
    LazyColumn(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(catalog.lines, key = PassengerNetworkLine::code) { line ->
            val expanded = expandedLineCode == line.code
            val orderedStationCodes = NetworkMapGeometry.lines
                .firstOrNull { it.code == line.code }
                ?.stationCodes
                .orEmpty()
            Card(
                modifier = Modifier.fillMaxWidth(),
                onClick = { expandedLineCode = if (expanded) null else line.code },
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LineBadge(line.code, line.color)
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                        Text(
                            stringResource(R.string.journeys_station_count, orderedStationCodes.size),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                    Text(
                        text = if (expanded) "−" else "+",
                        style = MaterialTheme.typography.headlineSmall,
                        color = resolvedLineColor(line.code, line.color, MaterialTheme.colorScheme.primary),
                    )
                }
                if (expanded) {
                    Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                        orderedStationCodes.forEachIndexed { index, stationCode ->
                            val station = stationsByCode[stationCode] ?: return@forEachIndexed
                            LineStationRow(
                                stationName = station.name,
                                stationCode = station.code,
                                lineColor = resolvedLineColor(line.code, line.color, MaterialTheme.colorScheme.primary),
                                correspondenceLines = station.lineCodes
                                    .filterNot { it == line.code }
                                    .mapNotNull(linesByCode::get),
                                first = index == 0,
                                last = index == orderedStationCodes.lastIndex,
                            )
                        }
                    }
                }
                }
            }
        }
    }
}

@Composable
internal fun LineBadge(code: String, color: String?) {
    val background = resolvedLineColor(code, color, MaterialTheme.colorScheme.primary)
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
private fun LineStationRow(
    stationName: String,
    stationCode: String,
    lineColor: Color,
    correspondenceLines: List<PassengerNetworkLine>,
    first: Boolean,
    last: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                val railX = 12.dp.toPx()
                val centerY = size.height / 2f
                drawLine(
                    color = lineColor,
                    start = Offset(railX, if (first) centerY else 0f),
                    end = Offset(railX, if (last) centerY else size.height),
                    strokeWidth = 3.dp.toPx(),
                )
            }
            .padding(vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(if (correspondenceLines.isEmpty()) 14.dp else 20.dp)
                    .background(MaterialTheme.colorScheme.surface, CircleShape)
                    .border(3.dp, lineColor, CircleShape),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(stationName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(
                stationCode,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            correspondenceLines.forEach { correspondence ->
                LineBadge(correspondence.code, correspondence.color)
            }
        }
    }
}

@Composable
internal fun EmptyState(message: Int) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            stringResource(message),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

