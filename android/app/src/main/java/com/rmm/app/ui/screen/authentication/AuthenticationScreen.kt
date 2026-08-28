package com.rmm.app.ui.screen.authentication

import android.util.Patterns
import android.content.Context
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.rmm.app.R
import com.rmm.app.core.auth.AuthenticationResult
import com.rmm.app.core.auth.PassengerAuthenticationRepository
import com.rmm.app.core.auth.PassengerRegistrationResult
import com.rmm.app.core.network.ApiFailure
import com.rmm.app.core.session.PassengerSession
import com.rmm.app.ui.component.RMMBrandMark
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class AuthenticationMode { LOGIN, REGISTER }

@Composable
fun AuthenticationScreen(
    onAuthenticated: (PassengerSession) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val repository = remember(context) { PassengerAuthenticationRepository(context) }
    val scope = rememberCoroutineScope()
    var mode by rememberSaveable { mutableStateOf(AuthenticationMode.LOGIN) }
    var loading by rememberSaveable { mutableStateOf(false) }
    var feedback by rememberSaveable { mutableStateOf<String?>(null) }
    var feedbackIsError by rememberSaveable { mutableStateOf(true) }

    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            RMMBrandMark(size = 64.dp)
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.auth_welcome), style = MaterialTheme.typography.headlineMedium)
            Text(
                stringResource(R.string.app_subtitle),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(28.dp))

            Column(modifier = Modifier.fillMaxWidth()) {
                TabRow(selectedTabIndex = mode.ordinal) {
                    Tab(
                        selected = mode == AuthenticationMode.LOGIN,
                        onClick = { mode = AuthenticationMode.LOGIN; feedback = null },
                        text = { Text(stringResource(R.string.auth_login_tab)) },
                    )
                    Tab(
                        selected = mode == AuthenticationMode.REGISTER,
                        onClick = { mode = AuthenticationMode.REGISTER; feedback = null },
                        text = { Text(stringResource(R.string.auth_register_tab)) },
                    )
                }
                Spacer(Modifier.height(24.dp))

                if (mode == AuthenticationMode.LOGIN) {
                    LoginForm(
                        loading = loading,
                        feedback = feedback,
                        feedbackIsError = feedbackIsError,
                        onSubmit = { email, password ->
                            loading = true
                            feedback = null
                            feedbackIsError = true
                            scope.launch {
                                when (val result = repository.login(email, password)) {
                                    is AuthenticationResult.Authenticated -> onAuthenticated(result.session)
                                    is AuthenticationResult.Failure -> feedback = context.authError(result.reason)
                                    AuthenticationResult.StorageFailure -> {
                                        feedback = context.getString(R.string.auth_session_storage_error)
                                    }
                                }
                                loading = false
                            }
                        },
                    )
                } else {
                    RegistrationForm(
                        loading = loading,
                        feedback = feedback,
                        feedbackIsError = feedbackIsError,
                        onSubmit = { email, password, firstName, lastName ->
                            loading = true
                            feedback = null
                            feedbackIsError = true
                            scope.launch {
                                when (val result = repository.registerAndAuthenticate(
                                    email, password, firstName, lastName,
                                )) {
                                    is PassengerRegistrationResult.Authenticated -> {
                                        onAuthenticated(result.session)
                                    }
                                    is PassengerRegistrationResult.VerificationRequired -> {
                                        mode = AuthenticationMode.LOGIN
                                        feedback = context.getString(
                                            R.string.auth_registration_verification_required,
                                            result.email,
                                        )
                                        feedbackIsError = false
                                    }
                                    is PassengerRegistrationResult.Failure -> {
                                        feedback = context.authError(result.reason)
                                    }
                                    PassengerRegistrationResult.StorageFailure -> {
                                        feedback = context.getString(R.string.auth_session_storage_error)
                                    }
                                }
                                loading = false
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun LoginForm(
    loading: Boolean,
    feedback: String?,
    feedbackIsError: Boolean,
    onSubmit: (String, String) -> Unit,
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val emailFocus = remember { FocusRequester() }
    val passwordFocus = remember { FocusRequester() }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var validation by rememberSaveable { mutableStateOf<String?>(null) }

    Text(stringResource(R.string.auth_login_title), style = MaterialTheme.typography.titleLarge)
    Spacer(Modifier.height(16.dp))
    val submit = {
        validation = validateLogin(context, email, password)
        if (validation == null) {
            focusManager.clearFocus()
            onSubmit(email, password)
        }
    }

    EmailField(
        value = email,
        onValueChange = { email = it },
        enabled = !loading,
        focusRequester = emailFocus,
        onNext = { passwordFocus.requestFocus() },
    )
    Spacer(Modifier.height(12.dp))
    PasswordField(
        password,
        { password = it },
        enabled = !loading,
        imeAction = ImeAction.Done,
        onImeAction = submit,
        focusRequester = passwordFocus,
    )
    Feedback(
        message = validation ?: feedback,
        isError = validation != null || feedbackIsError,
    )
    SubmitButton(loading, R.string.auth_login_action, submit)
}

@Composable
private fun RegistrationForm(
    loading: Boolean,
    feedback: String?,
    feedbackIsError: Boolean,
    onSubmit: (String, String, String, String) -> Unit,
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val firstNameFocus = remember { FocusRequester() }
    val lastNameFocus = remember { FocusRequester() }
    val emailFocus = remember { FocusRequester() }
    val passwordFocus = remember { FocusRequester() }
    val confirmationFocus = remember { FocusRequester() }
    var firstName by rememberSaveable { mutableStateOf("") }
    var lastName by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirmation by rememberSaveable { mutableStateOf("") }
    var termsAccepted by rememberSaveable { mutableStateOf(false) }
    var validation by rememberSaveable { mutableStateOf<String?>(null) }

    val submit = {
        validation = validateRegistration(
            context,
            firstName, lastName, email, password, confirmation, termsAccepted,
        )
        if (validation == null) {
            focusManager.clearFocus()
            onSubmit(email, password, firstName, lastName)
        }
    }

    Text(stringResource(R.string.auth_register_title), style = MaterialTheme.typography.titleLarge)
    Spacer(Modifier.height(16.dp))
    OutlinedTextField(
        value = firstName,
        onValueChange = { firstName = it },
        label = { Text(stringResource(R.string.auth_first_name)) },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Next,
            showKeyboardOnFocus = true,
        ),
        keyboardActions = KeyboardActions(
            onNext = { lastNameFocus.requestFocus() },
        ),
        singleLine = true,
        enabled = !loading,
        modifier = authenticationFieldModifier(firstNameFocus),
    )
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = lastName,
        onValueChange = { lastName = it },
        label = { Text(stringResource(R.string.auth_last_name)) },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Next,
            showKeyboardOnFocus = true,
        ),
        keyboardActions = KeyboardActions(
            onNext = { emailFocus.requestFocus() },
        ),
        singleLine = true,
        enabled = !loading,
        modifier = authenticationFieldModifier(lastNameFocus),
    )
    Spacer(Modifier.height(12.dp))
    EmailField(
        value = email,
        onValueChange = { email = it },
        enabled = !loading,
        focusRequester = emailFocus,
        onNext = { passwordFocus.requestFocus() },
    )
    Spacer(Modifier.height(12.dp))
    PasswordField(
        value = password,
        onValueChange = { password = it },
        enabled = !loading,
        focusRequester = passwordFocus,
        onNext = { confirmationFocus.requestFocus() },
    )
    Spacer(Modifier.height(12.dp))
    PasswordField(
        value = confirmation,
        onValueChange = { confirmation = it },
        enabled = !loading,
        label = R.string.auth_password_confirmation,
        imeAction = ImeAction.Done,
        onImeAction = submit,
        focusRequester = confirmationFocus,
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = termsAccepted,
            onCheckedChange = { termsAccepted = it },
            enabled = !loading,
        )
        Text(stringResource(R.string.auth_terms_acceptance), style = MaterialTheme.typography.bodyMedium)
    }
    Feedback(
        message = validation ?: feedback,
        isError = validation != null || feedbackIsError,
    )
    SubmitButton(loading, R.string.auth_register_action, submit)
}

@Composable
private fun EmailField(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    focusRequester: FocusRequester,
    onNext: () -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(R.string.auth_email)) },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Next,
            showKeyboardOnFocus = true,
        ),
        keyboardActions = KeyboardActions(
            onNext = { onNext() },
        ),
        singleLine = true,
        enabled = enabled,
        modifier = authenticationFieldModifier(focusRequester),
    )
}

@Composable
private fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    label: Int = R.string.auth_password,
    imeAction: ImeAction = ImeAction.Next,
    onImeAction: (() -> Unit)? = null,
    focusRequester: FocusRequester,
    onNext: (() -> Unit)? = null,
) {
    val focusManager = LocalFocusManager.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(label)) },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = imeAction,
            showKeyboardOnFocus = true,
        ),
        keyboardActions = if (imeAction == ImeAction.Done) {
            KeyboardActions(onDone = { onImeAction?.invoke() ?: focusManager.clearFocus() })
        } else {
            KeyboardActions(onNext = { onNext?.invoke() })
        },
        singleLine = true,
        enabled = enabled,
        modifier = authenticationFieldModifier(focusRequester),
    )
}

@Composable
private fun authenticationFieldModifier(focusRequester: FocusRequester): Modifier {
    val view = LocalView.current
    val inputMethodManager = remember(view) {
        view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(focused) {
        if (focused) {
            delay(150)
            inputMethodManager.showSoftInput(view, InputMethodManager.SHOW_FORCED)
        }
    }
    return Modifier
        .focusRequester(focusRequester)
        .fillMaxWidth()
        .pointerInput(focusRequester) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                focusRequester.requestFocus()
                waitForUpOrCancellation(pass = PointerEventPass.Initial)
            }
        }
        .onFocusChanged { focused = it.isFocused }
}

@Composable
private fun Feedback(message: String?, isError: Boolean) {
    if (message != null) {
        Spacer(Modifier.height(12.dp))
        Text(
            text = message,
            color = if (isError) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.primary
            },
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun SubmitButton(loading: Boolean, label: Int, onClick: () -> Unit) {
    Spacer(Modifier.height(16.dp))
    Button(onClick = onClick, enabled = !loading, modifier = Modifier.fillMaxWidth()) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.height(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        } else {
            Text(stringResource(label))
        }
    }
}

private fun validateLogin(context: android.content.Context, email: String, password: String): String? = when {
    !Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches() -> context.getString(R.string.auth_validation_email)
    password.isBlank() -> context.getString(R.string.auth_validation_password_required)
    else -> null
}

private fun validateRegistration(
    context: android.content.Context,
    firstName: String,
    lastName: String,
    email: String,
    password: String,
    confirmation: String,
    termsAccepted: Boolean,
): String? = when {
    firstName.isBlank() || lastName.isBlank() -> context.getString(R.string.auth_validation_name)
    !Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches() -> context.getString(R.string.auth_validation_email)
    password.length !in 12..72 -> context.getString(R.string.auth_validation_password_length)
    !password.any(Char::isLowerCase) || !password.any(Char::isUpperCase) || !password.any(Char::isDigit) ->
        context.getString(R.string.auth_validation_password_complexity)
    password != confirmation -> context.getString(R.string.auth_validation_password_mismatch)
    !termsAccepted -> context.getString(R.string.auth_validation_terms)
    else -> null
}

private fun android.content.Context.authError(failure: ApiFailure): String =
    getString(authenticationErrorMessage(failure))
