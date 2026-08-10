package com.rmm.app.ui.screen.journeys

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.rmm.app.R
import com.rmm.app.core.network.ApiResult
import com.rmm.app.core.networkcatalog.NetworkCatalog
import com.rmm.app.core.networkcatalog.PassengerNetworkJourney
import com.rmm.app.core.networkcatalog.PassengerNetworkJourneySegment
import com.rmm.app.core.networkcatalog.PassengerNetworkRepository
import com.rmm.app.core.networkcatalog.PassengerNetworkStation
import com.rmm.app.core.session.PassengerSession
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import kotlin.math.ceil

private enum class PickerTarget { ORIGIN, DESTINATION }

private sealed interface JourneyUiState {
    data object Empty : JourneyUiState
    data object Loading : JourneyUiState
    data class Content(val journey: PassengerNetworkJourney) : JourneyUiState
    data object Error : JourneyUiState
}

@Composable
internal fun JourneyPlanner(
    session: PassengerSession,
    catalog: NetworkCatalog,
    repository: PassengerNetworkRepository,
) {
    val scope = rememberCoroutineScope()
    var originCode by rememberSaveable { mutableStateOf<String?>(null) }
    var destinationCode by rememberSaveable { mutableStateOf<String?>(null) }
    var picker by remember { mutableStateOf<PickerTarget?>(null) }
    var sameStationError by remember { mutableStateOf(false) }
    var state by remember { mutableStateOf<JourneyUiState>(JourneyUiState.Empty) }
    val stationsByCode = remember(catalog.stations) { catalog.stations.associateBy { it.code } }

    fun calculate() {
        val origin = originCode ?: return
        val destination = destinationCode ?: return
        if (origin == destination) {
            sameStationError = true
            return
        }
        sameStationError = false
        scope.launch {
            state = JourneyUiState.Loading
            state = when (val result = repository.journey(session, origin, destination)) {
                is ApiResult.Success -> JourneyUiState.Content(result.value)
                is ApiResult.Failure -> JourneyUiState.Error
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(stringResource(R.string.journeys_planner_title), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(R.string.journeys_planner_description),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            StationSelector(
                label = stringResource(R.string.journeys_origin),
                station = originCode?.let(stationsByCode::get),
                onClick = { picker = PickerTarget.ORIGIN },
            )
        }
        item {
            TextButton(
                onClick = {
                    val previousOrigin = originCode
                    originCode = destinationCode
                    destinationCode = previousOrigin
                    state = JourneyUiState.Empty
                    sameStationError = false
                },
                enabled = originCode != null || destinationCode != null,
            ) { Text(stringResource(R.string.journeys_swap_stations)) }
        }
        item {
            StationSelector(
                label = stringResource(R.string.journeys_destination),
                station = destinationCode?.let(stationsByCode::get),
                onClick = { picker = PickerTarget.DESTINATION },
            )
        }
        if (sameStationError) {
            item {
                Text(
                    stringResource(R.string.journeys_same_station_error),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        item {
            Button(
                onClick = ::calculate,
                enabled = originCode != null && destinationCode != null && state !is JourneyUiState.Loading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state is JourneyUiState.Loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(2.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text(stringResource(R.string.journeys_calculate))
                }
            }
        }
        when (val current = state) {
            JourneyUiState.Empty, JourneyUiState.Loading -> Unit
            JourneyUiState.Error -> item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.journeys_calculation_error),
                        color = MaterialTheme.colorScheme.error,
                    )
                    OutlinedButton(onClick = ::calculate) {
                        Text(stringResource(R.string.action_retry))
                    }
                }
            }
            is JourneyUiState.Content -> {
                item { JourneySummary(current.journey) }
                items(current.journey.segments, key = { "${it.lineCode}-${it.stations.firstOrNull()?.code}" }) {
                    JourneySegmentCard(it)
                }
            }
        }
    }

    picker?.let { target ->
        StationPickerDialog(
            title = stringResource(
                if (target == PickerTarget.ORIGIN) R.string.journeys_select_origin_title
                else R.string.journeys_select_destination_title,
            ),
            catalog = catalog,
            selectedStationCode = if (target == PickerTarget.ORIGIN) originCode else destinationCode,
            onStationSelected = { station ->
                if (target == PickerTarget.ORIGIN) originCode = station.code else destinationCode = station.code
                picker = null
                state = JourneyUiState.Empty
                sameStationError = false
            },
            onDismiss = { picker = null },
        )
    }
}

@Composable
private fun StationSelector(label: String, station: PassengerNetworkStation?, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(
                    station?.name ?: stringResource(R.string.journeys_select_station),
                    style = MaterialTheme.typography.titleMedium,
                )
                station?.let {
                    Text(it.code, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(
                stringResource(
                    if (station == null) R.string.journeys_select_station else R.string.journeys_change_station,
                ),
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun StationPickerDialog(
    title: String,
    catalog: NetworkCatalog,
    selectedStationCode: String?,
    onStationSelected: (PassengerNetworkStation) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxSize()) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.journeys_close_picker)) }
                }
                StationSearch(
                    catalog = catalog,
                    selectedStationCode = selectedStationCode,
                    onStationSelected = onStationSelected,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun JourneySummary(journey: PassengerNetworkJourney) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.journeys_result_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(
                    R.string.journeys_duration_minutes,
                    ceil(journey.estimatedDurationSeconds / 60.0).toInt(),
                ),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(pluralStringResource(R.plurals.journeys_result_stations, journey.stationCount, journey.stationCount))
                Text(pluralStringResource(R.plurals.journeys_result_transfers, journey.transferCount, journey.transferCount))
            }
            Text("${journey.origin.name} — ${journey.destination.name}")
        }
    }
}

@Composable
private fun JourneySegmentCard(segment: PassengerNetworkJourneySegment) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LineBadge(segment.lineCode, segment.lineColor)
                Column(modifier = Modifier.weight(1f)) {
                    Text(segment.lineName, style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.journeys_direction, segment.directionTerminal.name))
                }
                Text(stringResource(R.string.journeys_segment_duration, ceil(segment.travelSeconds / 60.0).toInt()))
            }
            HorizontalDivider()
            Text(
                pluralStringResource(R.plurals.journeys_segment_stops, segment.stopCount, segment.stopCount),
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                segment.stations.joinToString("  ·  ") { it.name },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
