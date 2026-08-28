package com.rmm.app.ui.screen.tickets

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.view.WindowManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.rmm.app.R
import com.rmm.app.core.network.ApiResult
import com.rmm.app.core.session.PassengerSession
import com.rmm.app.core.ticketwallet.PassengerTicketQr
import com.rmm.app.core.ticketwallet.PassengerTicketWalletRepository

private sealed interface TicketQrUiState {
    data object Loading : TicketQrUiState
    data class Content(val credential: PassengerTicketQr) : TicketQrUiState
    data object Error : TicketQrUiState
}

@Composable
internal fun TicketQrDialog(
    ticketCode: String,
    session: PassengerSession,
    repository: PassengerTicketWalletRepository,
    onDismiss: () -> Unit,
) {
    var retryKey by remember { mutableIntStateOf(0) }
    var state by remember(ticketCode) { mutableStateOf<TicketQrUiState>(TicketQrUiState.Loading) }
    SecureQrWindow()

    LaunchedEffect(ticketCode, session.accessToken, retryKey) {
        state = TicketQrUiState.Loading
        state = when (val result = repository.ticketQr(session, ticketCode)) {
            is ApiResult.Success -> TicketQrUiState.Content(result.value)
            is ApiResult.Failure -> TicketQrUiState.Error
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ticket_qr_title)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                when (val current = state) {
                    TicketQrUiState.Loading -> {
                        CircularProgressIndicator()
                        Text(stringResource(R.string.ticket_qr_loading))
                    }
                    TicketQrUiState.Error -> {
                        Text(stringResource(R.string.ticket_qr_error))
                        Button(onClick = { retryKey++ }) {
                            Text(stringResource(R.string.action_retry))
                        }
                    }
                    is TicketQrUiState.Content -> {
                        val bitmap = remember(current.credential.qrValue) {
                            qrBitmap(current.credential.qrValue)
                        }
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = stringResource(R.string.ticket_qr_title),
                            modifier = Modifier.size(280.dp),
                        )
                        Text(current.credential.ticketCode)
                        Text(stringResource(R.string.ticket_qr_instructions))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.ticket_qr_close)) }
        },
    )
}

@Composable
private fun SecureQrWindow() {
    val activity = LocalContext.current.activity() ?: return
    DisposableEffect(activity) {
        val window = activity.window
        val hadSecureFlag = window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0
        val previousBrightness = window.attributes.screenBrightness
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        window.attributes = window.attributes.apply { screenBrightness = 1f }
        onDispose {
            window.attributes = window.attributes.apply { screenBrightness = previousBrightness }
            if (!hadSecureFlag) window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}

private fun qrBitmap(value: String, size: Int = 900): Bitmap {
    val matrix = QRCodeWriter().encode(value, BarcodeFormat.QR_CODE, size, size)
    val pixels = IntArray(size * size)
    for (y in 0 until size) {
        for (x in 0 until size) {
            pixels[y * size + x] = if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE
        }
    }
    return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
}

private tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}
