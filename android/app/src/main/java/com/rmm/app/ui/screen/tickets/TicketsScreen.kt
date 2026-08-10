package com.rmm.app.ui.screen.tickets

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import com.rmm.app.core.network.ApiFailure
import com.rmm.app.core.network.ApiResult
import com.rmm.app.core.session.PassengerSession
import com.rmm.app.core.ticketcatalog.PassengerTicketCatalogRepository
import com.rmm.app.core.ticketcatalog.PassengerTicketProduct
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

private sealed interface TicketCatalogUiState {
    data object Loading : TicketCatalogUiState
    data class Content(val products: List<PassengerTicketProduct>) : TicketCatalogUiState
    data class Error(val failure: ApiFailure) : TicketCatalogUiState
}

@Composable
fun TicketsScreen(
    session: PassengerSession,
    modifier: Modifier = Modifier,
) {
    val repository = remember { PassengerTicketCatalogRepository() }
    var reloadKey by rememberSaveable { mutableIntStateOf(0) }
    var state by remember { mutableStateOf<TicketCatalogUiState>(TicketCatalogUiState.Loading) }
    var singleTripProduct by remember { mutableStateOf<PassengerTicketProduct?>(null) }
    var multiTripProduct by remember { mutableStateOf<PassengerTicketProduct?>(null) }

    LaunchedEffect(session.accessToken, reloadKey) {
        state = TicketCatalogUiState.Loading
        state = when (val result = repository.products(session)) {
            is ApiResult.Success -> TicketCatalogUiState.Content(result.value)
            is ApiResult.Failure -> TicketCatalogUiState.Error(result.reason)
        }
    }

    when (val current = state) {
        TicketCatalogUiState.Loading -> CatalogLoading(modifier)
        is TicketCatalogUiState.Error -> CatalogError(current.failure, modifier) { reloadKey++ }
        is TicketCatalogUiState.Content -> TicketCatalog(
            products = current.products,
            modifier = modifier,
            onConfigureSingleTrip = { singleTripProduct = it },
            onConfigureMultiTrip = { multiTripProduct = it },
        )
    }

    singleTripProduct?.let { product ->
        SingleTripConfigurationDialog(
            session = session,
            product = product,
            onDismiss = { singleTripProduct = null },
        )
    }
    multiTripProduct?.let { product ->
        MultiTripConfigurationDialog(
            product = product,
            onDismiss = { multiTripProduct = null },
        )
    }
}

@Composable
private fun TicketCatalog(
    products: List<PassengerTicketProduct>,
    modifier: Modifier,
    onConfigureSingleTrip: (PassengerTicketProduct) -> Unit,
    onConfigureMultiTrip: (PassengerTicketProduct) -> Unit,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text(stringResource(R.string.ticket_catalog_title), style = MaterialTheme.typography.headlineMedium)
            Text(
                stringResource(R.string.ticket_catalog_description),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (products.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.ticket_catalog_empty),
                    modifier = Modifier.padding(vertical = 32.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(products, key = PassengerTicketProduct::code) { product ->
                TicketProductCard(
                    product = product,
                    onConfigure = when (product.type) {
                        "SINGLE_TRIP" -> { { onConfigureSingleTrip(product) } }
                        "MULTI_TRIP" -> { { onConfigureMultiTrip(product) } }
                        else -> null
                    },
                )
            }
        }
    }
}

@Composable
private fun TicketProductCard(product: PassengerTicketProduct, onConfigure: (() -> Unit)?) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.onSurface, MaterialTheme.shapes.large),
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
                    color = MaterialTheme.colorScheme.onSurface,
                    contentColor = MaterialTheme.colorScheme.surface,
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text(
                        product.code.take(3),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(product.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        productTypeLabel(product.type),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                Text(
                    product.primaryPrice(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
            product.description?.takeIf(String::isNotBlank)?.let {
                Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                product.rules().forEach { rule -> RuleLabel(rule) }
                if (product.rechargeable) RuleLabel(stringResource(R.string.ticket_catalog_rechargeable))
            }
            onConfigure?.let {
                Button(onClick = it, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.ticket_configure))
                }
            }
        }
    }
}

@Composable
private fun RuleLabel(text: String) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small) {
        Text(text, modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun productTypeLabel(type: String): String = stringResource(
    when (type) {
        "SINGLE_TRIP" -> R.string.ticket_type_single_trip
        "MULTI_TRIP" -> R.string.ticket_type_multi_trip
        "TIME_PASS" -> R.string.ticket_type_time_pass
        "SMART_BALANCE" -> R.string.ticket_type_smart_balance
        else -> R.string.ticket_type_unknown
    },
)

@Composable
private fun PassengerTicketProduct.primaryPrice(): String = when (type) {
    "SINGLE_TRIP" -> stringResource(
        R.string.ticket_price_base_and_station,
        basePrice.money(currency), pricePerStation.money(currency),
    )
    "MULTI_TRIP" -> stringResource(R.string.ticket_price_per_trip, pricePerTrip.money(currency))
    "TIME_PASS" -> stringResource(R.string.ticket_price_per_day, pricePerDay.money(currency))
    "SMART_BALANCE" -> stringResource(R.string.ticket_price_from, minRechargeAmount?.money(currency) ?: "—")
    else -> basePrice.money(currency)
}

@Composable
private fun PassengerTicketProduct.rules(): List<String> = buildList {
    if (requiresOriginDestination) add(stringResource(R.string.ticket_rule_origin_destination))
    if (minTrips != null && maxTrips != null) add(stringResource(R.string.ticket_rule_trip_range, minTrips, maxTrips))
    if (minDays != null && maxDays != null) add(stringResource(R.string.ticket_rule_day_range, minDays, maxDays))
    if (minRechargeAmount != null && maxRechargeAmount != null) {
        add(stringResource(R.string.ticket_rule_recharge_range, minRechargeAmount.money(currency), maxRechargeAmount.money(currency)))
    }
}

private fun BigDecimal.money(currencyCode: String): String = NumberFormat
    .getCurrencyInstance(Locale("es", "ES"))
    .apply { currency = Currency.getInstance(currencyCode) }
    .format(this)

@Composable
private fun CatalogLoading(modifier: Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
private fun CatalogError(failure: ApiFailure, modifier: Modifier, retry: () -> Unit) {
    Box(modifier = modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(
                    if (failure is ApiFailure.Network) R.string.ticket_catalog_network_error
                    else R.string.ticket_catalog_request_error,
                ),
            )
            Button(onClick = retry) { Text(stringResource(R.string.action_retry)) }
        }
    }
}
