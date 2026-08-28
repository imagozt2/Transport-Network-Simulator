package com.rmm.app.ui.screen.authentication

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.rmm.app.R
import com.rmm.app.ui.theme.RMMAppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AuthenticationFormInteractionTest {
    @get:Rule
    val compose = createComposeRule()

    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun loginAllowsWritingEmailAndPasswordWhileIdle() {
        showAuthenticationScreen()

        val email = compose.onNodeWithText(context.getString(R.string.auth_email)).assertIsEnabled()
        email.performClick().assertIsFocused()
        email.performTextInput("pasajero@rmm.local")
        email.assertTextContains("pasajero@rmm.local")

        val password = compose.onNodeWithText(context.getString(R.string.auth_password)).assertIsEnabled()
        password.performClick().assertIsFocused()
        password.performTextInput("ClaveSegura123")
        password.assertTextContains("ClaveSegura123")
    }

    @Test
    fun registrationAllowsWritingAllPassengerCredentialsWhileIdle() {
        showAuthenticationScreen()
        compose.onNodeWithText(context.getString(R.string.auth_register_tab)).performClick()

        input(R.string.auth_first_name, "María")
        input(R.string.auth_last_name, "Muñoz")
        input(R.string.auth_email, "maria.munoz@rmm.local")
        input(R.string.auth_password, "ClaveSegura123")
        input(R.string.auth_password_confirmation, "ClaveSegura123")
    }

    private fun showAuthenticationScreen() {
        compose.setContent {
            RMMAppTheme {
                AuthenticationScreen(onAuthenticated = {})
            }
        }
    }

    private fun input(label: Int, value: String) {
        val field = compose.onNodeWithText(context.getString(label)).assertIsEnabled()
        field.performScrollTo().performClick().assertIsFocused()
        field.performTextInput(value)
        field.assertTextContains(value)
    }
}
