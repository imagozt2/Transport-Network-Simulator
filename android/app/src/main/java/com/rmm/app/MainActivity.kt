package com.rmm.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import com.rmm.app.core.auth.PassengerAuthenticationRepository
import com.rmm.app.core.auth.SessionRenewalResult
import com.rmm.app.core.session.PassengerSessionStorage
import com.rmm.app.core.preferences.AppTheme
import com.rmm.app.core.preferences.DisplayPreferences
import com.rmm.app.core.preferences.DisplayPreferencesStore
import com.rmm.app.navigation.RMMNavigation
import com.rmm.app.navigation.RMMRootDestination
import com.rmm.app.navigation.resolveRootDestination
import com.rmm.app.ui.screen.authentication.AuthenticationScreen
import com.rmm.app.ui.theme.RMMAppTheme
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.delay
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val baseContext = LocalContext.current
            val store = remember(baseContext) { DisplayPreferencesStore.get(baseContext) }
            var displayPreferences by remember { mutableStateOf(store.load()) }
            val configuration = remember(displayPreferences.language) {
                android.content.res.Configuration(baseContext.resources.configuration).apply {
                    setLocale(Locale.forLanguageTag(displayPreferences.language.languageTag))
                }
            }
            val localizedContext = remember(configuration) {
                baseContext.createConfigurationContext(configuration)
            }

            CompositionLocalProvider(
                LocalContext provides localizedContext,
                LocalConfiguration provides configuration,
            ) {
                RMMAppTheme(darkTheme = displayPreferences.theme == AppTheme.DARK) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        RMMApp(
                            displayPreferences = displayPreferences,
                            onDisplayPreferencesChanged = { updated ->
                                store.save(updated)
                                displayPreferences = updated
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RMMApp(
    displayPreferences: DisplayPreferences = DisplayPreferences(),
    onDisplayPreferencesChanged: (DisplayPreferences) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val sessionStore = remember(context) { PassengerSessionStorage.get(context) }
    val authenticationRepository = remember(context) { PassengerAuthenticationRepository(context) }
    var session by remember { mutableStateOf(sessionStore.load()) }

    LaunchedEffect(session?.refreshToken) {
        var currentSession = session ?: return@LaunchedEffect

        while (true) {
            val renewalAt = currentSession.accessTokenExpiresAt.minus(RENEWAL_MARGIN)
            val waitMillis = Duration.between(Instant.now(), renewalAt)
                .toMillis()
                .coerceAtLeast(0L)
            delay(waitMillis)

            when (val renewal = authenticationRepository.renewSession(currentSession)) {
                is SessionRenewalResult.Renewed -> {
                    session = renewal.session
                    return@LaunchedEffect
                }
                is SessionRenewalResult.Invalidated -> {
                    session = null
                    return@LaunchedEffect
                }
                is SessionRenewalResult.RetryableFailure -> {
                    if (!currentSession.canBeRefreshed()) {
                        authenticationRepository.discardSession()
                        session = null
                        return@LaunchedEffect
                    }
                    delay(RENEWAL_RETRY_DELAY.toMillis())
                    currentSession = session ?: return@LaunchedEffect
                }
            }
        }
    }

    when (resolveRootDestination(session)) {
        RMMRootDestination.AUTHENTICATION -> {
            AuthenticationScreen(
                onAuthenticated = { authenticatedSession -> session = authenticatedSession },
                modifier = modifier.fillMaxSize(),
            )
        }
        RMMRootDestination.APPLICATION -> {
            RMMNavigation(
                session = checkNotNull(session),
                onLoggedOut = { session = null },
                displayPreferences = displayPreferences,
                onDisplayPreferencesChanged = onDisplayPreferencesChanged,
                modifier = modifier.fillMaxSize(),
            )
        }
    }
}

private val RENEWAL_MARGIN: Duration = Duration.ofSeconds(30)
private val RENEWAL_RETRY_DELAY: Duration = Duration.ofSeconds(30)

@Preview(showBackground = true)
@Composable
private fun RMMAppPreview() {
    RMMAppTheme {
        RMMApp()
    }
}

