package com.rmm.app.ui.screen.journeys

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import com.rmm.app.R
import com.rmm.app.core.networkcatalog.NetworkCatalog
import com.rmm.app.core.networkcatalog.PassengerNetworkStation
import java.text.Normalizer
import java.util.Locale

@Composable
internal fun StationSearch(
    catalog: NetworkCatalog,
    selectedStationCode: String?,
    onStationSelected: (PassengerNetworkStation) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var selectedLineCode by rememberSaveable { mutableStateOf<String?>(null) }
    val filteredStations = remember(catalog.stations, query, selectedLineCode) {
        filterStations(catalog.stations, query, selectedLineCode)
    }
    val lineColors = remember(catalog.lines) { catalog.lines.associate { it.code to it.color } }

    Column(modifier = modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text(stringResource(R.string.journeys_search_station)) },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null)
            },
            trailingIcon = if (query.isNotEmpty()) {
                {
                    IconButton(onClick = { query = "" }) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.journeys_clear_search),
                        )
                    }
                }
            } else {
                null
            },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
        )
        Spacer(Modifier.height(8.dp))
        LazyRow(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "all-lines") {
                FilterChip(
                    selected = selectedLineCode == null,
                    onClick = { selectedLineCode = null },
                    label = { Text(stringResource(R.string.journeys_all_lines)) },
                )
            }
            items(catalog.lines, key = { it.code }) { line ->
                FilterChip(
                    selected = selectedLineCode == line.code,
                    onClick = {
                        selectedLineCode = line.code.takeUnless { it == selectedLineCode }
                    },
                    label = { Text(line.code) },
                )
            }
        }
        Text(
            text = pluralStringResource(
                R.plurals.journeys_search_results,
                filteredStations.size,
                filteredStations.size,
            ),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
        )

        if (filteredStations.isEmpty()) {
            EmptyState(R.string.journeys_no_stations)
        } else {
            LazyColumn(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    bottom = 20.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(filteredStations, key = PassengerNetworkStation::code) { station ->
                    val selected = selectedStationCode == station.code
                    Card(
                        onClick = { onStationSelected(station) },
                        modifier = Modifier.fillMaxWidth(),
                        border = if (selected) {
                            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                        } else {
                            null
                        },
                    ) {
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
                            if (selected) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = stringResource(R.string.journeys_station_selected),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

internal fun filterStations(
    stations: List<PassengerNetworkStation>,
    query: String,
    lineCode: String?,
): List<PassengerNetworkStation> {
    val normalizedQuery = query.normalizedForSearch()
    return stations.filter { station ->
        val matchesQuery = normalizedQuery.isEmpty()
            || station.name.normalizedForSearch().contains(normalizedQuery)
            || station.code.normalizedForSearch().contains(normalizedQuery)
        val matchesLine = lineCode == null || lineCode in station.lineCodes
        matchesQuery && matchesLine
    }
}

private fun String.normalizedForSearch(): String = Normalizer
    .normalize(trim(), Normalizer.Form.NFD)
    .replace(COMBINING_MARKS, "")
    .lowercase(Locale.ROOT)

private val COMBINING_MARKS = "\\p{M}+".toRegex()
