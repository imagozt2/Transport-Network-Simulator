package com.rmm.app.ui.screen.journeys

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rmm.app.R
import com.rmm.app.core.journeyhistory.PassengerJourneyHistoryItem
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

@Composable
internal fun JourneyDetailDialog(
    journey: PassengerJourneyHistoryItem,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.journey_detail_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                JourneyDetailRoute(journey)
                HorizontalDivider()
                JourneyDetailTimes(journey)
                HorizontalDivider()
                JourneyDetailMetrics(journey)
                HorizontalDivider()
                JourneyDetailTicket(journey)
                if (journey.anomalous) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                stringResource(R.string.journey_detail_anomaly_title),
                                fontWeight = FontWeight.Bold,
                            )
                            Text(stringResource(R.string.journey_detail_anomaly_description))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.journey_detail_close))
            }
        },
    )
}

@Composable
private fun JourneyDetailRoute(journey: PassengerJourneyHistoryItem) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        DetailStation(
            label = stringResource(R.string.journey_detail_entry),
            name = journey.origin.name,
            code = journey.origin.code,
        )
        DetailStation(
            label = stringResource(R.string.journey_detail_exit),
            name = journey.destination?.name ?: stringResource(R.string.journey_history_without_exit),
            code = journey.destination?.code,
        )
    }
}

@Composable
private fun DetailStation(label: String, name: String, code: String?) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        code?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
    }
}

@Composable
private fun JourneyDetailTimes(journey: PassengerJourneyHistoryItem) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        DetailValue(
            modifier = Modifier.weight(1f),
            label = stringResource(R.string.journey_detail_started_at),
            value = journey.openedAt.asJourneyDateTime(),
        )
        DetailValue(
            modifier = Modifier.weight(1f),
            label = stringResource(R.string.journey_detail_ended_at),
            value = journey.endedAt?.asJourneyDateTime()
                ?: stringResource(R.string.journey_detail_not_available),
        )
    }
}

@Composable
private fun JourneyDetailMetrics(journey: PassengerJourneyHistoryItem) {
    val stations = journey.stationCount?.toString()
        ?: stringResource(R.string.journey_detail_not_available)
    val duration = journey.durationSeconds?.let {
        stringResource(R.string.journey_history_duration, it / 60, it % 60)
    } ?: stringResource(R.string.journey_detail_not_available)
    val fare = journey.fareAmount?.let {
        NumberFormat.getCurrencyInstance(Locale("es", "ES")).apply {
            currency = Currency.getInstance(journey.currency)
        }.format(it)
    } ?: stringResource(R.string.journey_detail_not_available)
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        DetailValue(Modifier.weight(1f), stringResource(R.string.journey_detail_stations), stations)
        DetailValue(Modifier.weight(1f), stringResource(R.string.journey_detail_duration), duration)
        DetailValue(Modifier.weight(1f), stringResource(R.string.journey_detail_fare), fare)
    }
}

@Composable
private fun JourneyDetailTicket(journey: PassengerJourneyHistoryItem) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.journey_detail_transport_title),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(journey.productName, fontWeight = FontWeight.Bold)
        Text(journey.ticketCode, style = MaterialTheme.typography.labelSmall)
        Text(
            stringResource(R.string.journey_detail_reference, journey.code),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DetailValue(modifier: Modifier, label: String, value: String) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}
