package com.rmm.app.ui.screen.tickets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.window.Dialog
import com.rmm.app.R
import com.rmm.app.core.network.ApiFailure
import com.rmm.app.core.network.ApiResult
import com.rmm.app.core.networkcatalog.NetworkCatalog
import com.rmm.app.core.networkcatalog.NetworkCatalogResult
import com.rmm.app.core.networkcatalog.PassengerNetworkJourney
import com.rmm.app.core.networkcatalog.PassengerNetworkRepository
import com.rmm.app.core.networkcatalog.PassengerNetworkStation
import com.rmm.app.core.session.PassengerSession
import com.rmm.app.core.ticketcatalog.PassengerTicketProduct
import com.rmm.app.core.ticketpurchase.PassengerTicketPurchaseConfiguration
import com.rmm.app.ui.screen.journeys.StationSearch
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale
import kotlin.math.ceil

private enum class SingleTripStationTarget { ORIGIN, DESTINATION }

private sealed interface SingleTripCatalogState {
    data object Loading : SingleTripCatalogState
    data class Content(val catalog: NetworkCatalog) : SingleTripCatalogState
    data class Error(val failure: ApiFailure) : SingleTripCatalogState
}

private sealed interface SingleTripEstimateState {
    data object Empty : SingleTripEstimateState
    data object Loading : SingleTripEstimateState
    data class Content(val journey: PassengerNetworkJourney) : SingleTripEstimateState
    data object Error : SingleTripEstimateState
}

@Composable
internal fun SingleTripConfigurationDialog(
    session: PassengerSession,
    product: PassengerTicketProduct,
    onDismiss: () -> Unit,
    onConfigured: (TicketPurchaseDraft) -> Unit,
) {
    val repository = remember { PassengerNetworkRepository() }
    var reloadKey by rememberSaveable { mutableIntStateOf(0) }
    var catalogState by remember { mutableStateOf<SingleTripCatalogState>(SingleTripCatalogState.Loading) }
    var estimateState by remember { mutableStateOf<SingleTripEstimateState>(SingleTripEstimateState.Empty) }
    var originCode by rememberSaveable { mutableStateOf<String?>(null) }
    var destinationCode by rememberSaveable { mutableStateOf<String?>(null) }
    var stationTarget by remember { mutableStateOf<SingleTripStationTarget?>(null) }

    LaunchedEffect(session.accessToken, reloadKey) {
        catalogState = SingleTripCatalogState.Loading
        catalogState = when (val result = repository.catalog(session)) {
            is NetworkCatalogResult.Success -> SingleTripCatalogState.Content(result.catalog)
            is NetworkCatalogResult.Failure -> SingleTripCatalogState.Error(result.reason)
        }
    }

    LaunchedEffect(originCode, destinationCode) {
        val origin = originCode
        val destination = destinationCode
        if (origin == null || destination == null || origin == destination) {
            estimateState = SingleTripEstimateState.Empty
            return@LaunchedEffect
        }
        estimateState = SingleTripEstimateState.Loading
        estimateState = when (val result = repository.journey(session, origin, destination)) {
            is ApiResult.Success -> SingleTripEstimateState.Content(result.value)
            is ApiResult.Failure -> SingleTripEstimateState.Error
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(modifier = Modifier.fillMaxSize(), shape = MaterialTheme.shapes.large) {
            when (val current = catalogState) {
                SingleTripCatalogState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                is SingleTripCatalogState.Error -> ConfigurationError(
                    failure = current.failure,
                    onRetry = { reloadKey++ },
                    onDismiss = onDismiss,
                )
                is SingleTripCatalogState.Content -> SingleTripForm(
                    product = product,
                    catalog = current.catalog,
                    originCode = originCode,
                    destinationCode = destinationCode,
                    estimateState = estimateState,
                    onSelectOrigin = { stationTarget = SingleTripStationTarget.ORIGIN },
                    onSelectDestination = { stationTarget = SingleTripStationTarget.DESTINATION },
                    onSwap = {
                        val previousOrigin = originCode
                        originCode = destinationCode
                        destinationCode = previousOrigin
                    },
                    onDismiss = onDismiss,
                    onConfigured = onConfigured,
                )
            }
        }
    }

    val catalog = (catalogState as? SingleTripCatalogState.Content)?.catalog
    if (stationTarget != null && catalog != null) {
        StationSelectionDialog(
            title = stringResource(
                if (stationTarget == SingleTripStationTarget.ORIGIN) R.string.ticket_select_origin
                else R.string.ticket_select_destination,
            ),
            catalog = catalog,
            selectedCode = if (stationTarget == SingleTripStationTarget.ORIGIN) originCode else destinationCode,
            onSelected = { station ->
                if (stationTarget == SingleTripStationTarget.ORIGIN) originCode = station.code
                else destinationCode = station.code
                stationTarget = null
            },
            onDismiss = { stationTarget = null },
        )
    }
}

@Composable
private fun SingleTripForm(
    product: PassengerTicketProduct,
    catalog: NetworkCatalog,
    originCode: String?,
    destinationCode: String?,
    estimateState: SingleTripEstimateState,
    onSelectOrigin: () -> Unit,
    onSelectDestination: () -> Unit,
    onSwap: () -> Unit,
    onDismiss: () -> Unit,
    onConfigured: (TicketPurchaseDraft) -> Unit,
) {
    val stations = remember(catalog.stations) { catalog.stations.associateBy { it.code } }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(product.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.ticket_single_trip_configuration), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.ticket_close_configuration)) }
        }
        StationField(stringResource(R.string.journeys_origin), originCode?.let(stations::get), onSelectOrigin)
        OutlinedButton(onClick = onSwap, enabled = originCode != null || destinationCode != null) {
            Text(stringResource(R.string.journeys_swap_stations))
        }
        StationField(stringResource(R.string.journeys_destination), destinationCode?.let(stations::get), onSelectDestination)
        if (originCode != null && originCode == destinationCode) {
            Text(stringResource(R.string.journeys_same_station_error), color = MaterialTheme.colorScheme.error)
        }
        when (estimateState) {
            SingleTripEstimateState.Empty -> Text(
                stringResource(R.string.ticket_select_route_hint),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SingleTripEstimateState.Loading -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator()
                Text(stringResource(R.string.ticket_calculating_price))
            }
            SingleTripEstimateState.Error -> Text(stringResource(R.string.ticket_price_error), color = MaterialTheme.colorScheme.error)
            is SingleTripEstimateState.Content -> SingleTripEstimate(
                product = product,
                journey = estimateState.journey,
                onContinue = {
                    onConfigured(
                        TicketPurchaseDraft(
                            product = product,
                            configuration = PassengerTicketPurchaseConfiguration(
                                originStationCode = estimateState.journey.origin.code,
                                destinationStationCode = estimateState.journey.destination.code,
                            ),
                            totalAmount = product.basePrice + product.pricePerStation *
                                BigDecimal.valueOf(estimateState.journey.stationCount.toLong()),
                            originName = estimateState.journey.origin.name,
                            destinationName = estimateState.journey.destination.name,
                        ),
                    )
                },
            )
        }
    }
}

@Composable
private fun StationField(label: String, station: PassengerNetworkStation?, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(station?.name ?: stringResource(R.string.journeys_select_station), style = MaterialTheme.typography.titleMedium)
            station?.let { Text(it.code, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun SingleTripEstimate(
    product: PassengerTicketProduct,
    journey: PassengerNetworkJourney,
    onContinue: () -> Unit,
) {
    val price = product.basePrice + product.pricePerStation * BigDecimal.valueOf(journey.stationCount.toLong())
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(stringResource(R.string.ticket_configuration_ready), style = MaterialTheme.typography.titleMedium)
            Text(price.money(product.currency), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.ticket_estimated_price_notice), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.journeys_station_count, journey.stationCount))
                Text(stringResource(R.string.journeys_duration_minutes, ceil(journey.estimatedDurationSeconds / 60.0).toInt()))
            }
            Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.ticket_continue_purchase))
            }
        }
    }
}

@Composable
private fun StationSelectionDialog(
    title: String,
    catalog: NetworkCatalog,
    selectedCode: String?,
    onSelected: (PassengerNetworkStation) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(modifier = Modifier.fillMaxSize(), shape = MaterialTheme.shapes.large) {
            Column {
                Row(modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.journeys_close_picker)) }
                }
                StationSearch(catalog, selectedCode, onSelected, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ConfigurationError(failure: ApiFailure, onRetry: () -> Unit, onDismiss: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(if (failure is ApiFailure.Network) R.string.ticket_catalog_network_error else R.string.ticket_catalog_request_error))
            Button(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.ticket_close_configuration)) }
        }
    }
}

private fun BigDecimal.money(currencyCode: String): String = NumberFormat
    .getCurrencyInstance(Locale("es", "ES"))
    .apply { currency = Currency.getInstance(currencyCode) }
    .format(this)
