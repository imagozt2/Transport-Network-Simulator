package com.rmm.app.ui.screen.tickets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.rmm.app.R
import com.rmm.app.core.network.ApiFailure
import com.rmm.app.core.network.ApiResult
import com.rmm.app.core.session.PassengerSession
import com.rmm.app.core.ticketwallet.PassengerTicketWalletRepository
import kotlinx.coroutines.launch

@Composable
internal fun PhysicalTicketScannerButton(
    modifier: Modifier = Modifier,
    onScanned: (String) -> Unit,
    onError: () -> Unit,
) {
    val context = LocalContext.current
    val scanner = remember(context) {
        GmsBarcodeScanning.getClient(
            context,
            GmsBarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .enableAutoZoom()
                .build(),
        )
    }
    Button(
        modifier = modifier,
        onClick = {
            scanner.startScan()
                .addOnSuccessListener { barcode ->
                    barcode.rawValue
                        ?.trim()
                        ?.takeIf { it.startsWith("RMM:TICKET:") && it.length <= 4096 }
                        ?.let(onScanned)
                        ?: onError()
                }
                .addOnFailureListener { onError() }
        },
    ) {
        Text(stringResource(R.string.ticket_wallet_register_physical))
    }
}

@Composable
internal fun PhysicalTicketLinkDialog(
    qrValue: String,
    session: PassengerSession,
    repository: PassengerTicketWalletRepository,
    onDismiss: () -> Unit,
    onLinked: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var linkCode by remember(qrValue) { mutableStateOf("") }
    var submitting by remember(qrValue) { mutableStateOf(false) }
    var failure by remember(qrValue) { mutableStateOf<ApiFailure?>(null) }
    val normalizedCode = linkCode.filterNot(Char::isWhitespace).replace("-", "")

    AlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        title = { Text(stringResource(R.string.ticket_link_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.ticket_link_description))
                OutlinedTextField(
                    value = linkCode,
                    onValueChange = { value ->
                        linkCode = value.take(40)
                        failure = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.ticket_link_code)) },
                    enabled = !submitting,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = PasswordVisualTransformation(),
                )
                failure?.let {
                    Text(
                        text = stringResource(it.ticketLinkErrorResource()),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !submitting && normalizedCode.length in 4..32,
                onClick = {
                    submitting = true
                    failure = null
                    scope.launch {
                        when (val result = repository.linkPhysicalTicket(
                            session = session,
                            qrValue = qrValue,
                            linkCode = normalizedCode,
                        )) {
                            is ApiResult.Success -> onLinked()
                            is ApiResult.Failure -> {
                                submitting = false
                                failure = result.reason
                            }
                        }
                    }
                },
            ) {
                if (submitting) CircularProgressIndicator()
                else Text(stringResource(R.string.ticket_link_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !submitting) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

private fun ApiFailure.ticketLinkErrorResource(): Int = when (this) {
    is ApiFailure.Http -> when (statusCode) {
        409 -> R.string.ticket_link_already_registered
        422 -> R.string.ticket_link_invalid_proof
        else -> R.string.ticket_link_error
    }
    else -> R.string.ticket_link_error
}
