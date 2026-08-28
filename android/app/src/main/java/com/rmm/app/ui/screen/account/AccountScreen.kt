package com.rmm.app.ui.screen.account

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rmm.app.R
import com.rmm.app.core.auth.LogoutResult
import com.rmm.app.core.auth.PassengerAuthenticationRepository
import com.rmm.app.core.session.PassengerSession
import com.rmm.app.core.preferences.AppLanguage
import com.rmm.app.core.preferences.AppTheme
import com.rmm.app.core.preferences.DisplayPreferences
import kotlinx.coroutines.launch

@Composable
fun AccountScreen(
    session: PassengerSession,
    onLoggedOut: () -> Unit,
    displayPreferences: DisplayPreferences,
    onDisplayPreferencesChanged: (DisplayPreferences) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val repository = remember(context) { PassengerAuthenticationRepository(context) }
    val scope = rememberCoroutineScope()
    var confirmationVisible by rememberSaveable { mutableStateOf(false) }
    var loggingOut by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val user = session.user

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.account_title), style = MaterialTheme.typography.headlineMedium)
        Text(
            stringResource(R.string.account_description),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(20.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = user.initials(),
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = "${user.firstName} ${user.lastName}".trim(),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(user.email, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(stringResource(R.string.account_information), style = MaterialTheme.typography.titleMedium)
                AccountField(stringResource(R.string.account_status), localizedStatus(user.status))
                AccountField(stringResource(R.string.account_identifier), user.publicId)
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.settings_description),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                PreferenceSelector(
                    label = stringResource(R.string.settings_language),
                    firstLabel = stringResource(R.string.settings_language_spanish),
                    secondLabel = stringResource(R.string.settings_language_english),
                    firstSelected = displayPreferences.language == AppLanguage.SPANISH,
                    onFirstSelected = {
                        onDisplayPreferencesChanged(displayPreferences.copy(language = AppLanguage.SPANISH))
                    },
                    onSecondSelected = {
                        onDisplayPreferencesChanged(displayPreferences.copy(language = AppLanguage.ENGLISH))
                    },
                )
                PreferenceSelector(
                    label = stringResource(R.string.settings_theme),
                    firstLabel = stringResource(R.string.settings_theme_light),
                    secondLabel = stringResource(R.string.settings_theme_dark),
                    firstSelected = displayPreferences.theme == AppTheme.LIGHT,
                    onFirstSelected = {
                        onDisplayPreferencesChanged(displayPreferences.copy(theme = AppTheme.LIGHT))
                    },
                    onSecondSelected = {
                        onDisplayPreferencesChanged(displayPreferences.copy(theme = AppTheme.DARK))
                    },
                )
            }
        }

        Spacer(Modifier.height(4.dp))
        OutlinedButton(
            onClick = { confirmationVisible = true },
            enabled = !loggingOut,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (loggingOut) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                Text(stringResource(R.string.account_logout))
            }
        }
        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
    }

    if (confirmationVisible) {
        AlertDialog(
            onDismissRequest = { if (!loggingOut) confirmationVisible = false },
            title = { Text(stringResource(R.string.account_logout_confirmation_title)) },
            text = { Text(stringResource(R.string.account_logout_confirmation_description)) },
            confirmButton = {
                Button(
                    onClick = {
                        confirmationVisible = false
                        loggingOut = true
                        error = null
                        scope.launch {
                            when (repository.logout(session)) {
                                LogoutResult.Completed,
                                LogoutResult.CompletedLocally -> onLoggedOut()
                                LogoutResult.LocalStorageFailure -> {
                                    error = context.getString(R.string.account_logout_storage_error)
                                    loggingOut = false
                                }
                            }
                        }
                    },
                ) {
                    Text(stringResource(R.string.account_logout_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmationVisible = false }) {
                    Text(stringResource(R.string.account_logout_cancel))
                }
            },
        )
    }
}

@Composable
private fun AccountField(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = value, style = MaterialTheme.typography.bodyLarge)
    }
}

private fun com.rmm.app.core.session.PassengerSessionUser.initials(): String =
    listOf(firstName, lastName)
        .mapNotNull { it.trim().firstOrNull()?.uppercase() }
        .joinToString("")
        .take(2)
        .ifBlank { "M" }

@Composable
private fun localizedStatus(status: String): String = when (status) {
    "ACTIVE" -> stringResource(R.string.account_status_active)
    "PENDING_VERIFICATION" -> stringResource(R.string.account_status_pending)
    "DISABLED" -> stringResource(R.string.account_status_disabled)
    "BLOCKED" -> stringResource(R.string.account_status_blocked)
    else -> status
}

@Composable
private fun PreferenceSelector(
    label: String,
    firstLabel: String,
    secondLabel: String,
    firstSelected: Boolean,
    onFirstSelected: () -> Unit,
    onSecondSelected: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (firstSelected) {
                Button(onClick = onFirstSelected, modifier = Modifier.weight(1f)) { Text(firstLabel) }
                OutlinedButton(onClick = onSecondSelected, modifier = Modifier.weight(1f)) { Text(secondLabel) }
            } else {
                OutlinedButton(onClick = onFirstSelected, modifier = Modifier.weight(1f)) { Text(firstLabel) }
                Button(onClick = onSecondSelected, modifier = Modifier.weight(1f)) { Text(secondLabel) }
            }
        }
    }
}
