package com.avalemon.spotcheck.ui.signin

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SignInScreen(
    onSignedIn: () -> Unit,
    vm: SignInViewModel = viewModel(factory = SignInViewModel.Factory),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var showPassword by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(vm.signedIn) { if (vm.signedIn) onSignedIn() }
    LaunchedEffect(vm.info) { vm.info?.let { snackbar.showSnackbar(it); vm.consumeInfo() } }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Box(
            Modifier.fillMaxSize().padding(padding).imePadding(),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                Modifier
                    .widthIn(max = 480.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 32.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(88.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.Place, null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(48.dp),
                        )
                    }
                }
                Text("SpotCheck", style = MaterialTheme.typography.displayMediumEmphasized, color = MaterialTheme.colorScheme.primary)
                Text(
                    "Find and rate the best study rooms, restrooms and more — floor by floor.",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))

                AnimatedVisibility(vm.isCreateMode) {
                    OutlinedTextField(
                        value = vm.displayName,
                        onValueChange = vm::onName,
                        label = { Text("Display name") },
                        supportingText = { Text("Shown on your reviews") },
                        singleLine = true,
                        enabled = !vm.loading,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                OutlinedTextField(
                    value = vm.email,
                    onValueChange = vm::onEmail,
                    label = { Text("Email") },
                    singleLine = true,
                    enabled = !vm.loading,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = vm.password,
                    onValueChange = vm::onPassword,
                    label = { Text("Password") },
                    singleLine = true,
                    enabled = !vm.loading,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }, shapes = IconButtonDefaults.shapes()) {
                            Icon(
                                if (showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = if (showPassword) "Hide password" else "Show password",
                            )
                        }
                    },
                    supportingText = if (vm.isCreateMode) ({ Text("At least 6 characters") }) else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { vm.submitEmail() }),
                    modifier = Modifier.fillMaxWidth(),
                )

                vm.error?.let { ErrorBanner(it) }

                Button(
                    onClick = vm::submitEmail,
                    enabled = vm.canSubmit,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) {
                    if (vm.loading) {
                        LoadingIndicator(Modifier.size(28.dp), color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Text(if (vm.isCreateMode) "Create account" else "Sign in", style = MaterialTheme.typography.titleMedium)
                    }
                }
                if (!vm.isCreateMode) {
                    TextButton(onClick = vm::resetPassword, enabled = !vm.loading) { Text("Forgot password?") }
                }

                OrDivider()

                OutlinedButton(
                    onClick = {
                        vm.onGoogleStarted()
                        scope.launch {
                            when (val r = requestGoogleIdToken(context)) {
                                is GoogleResult.Token -> vm.signInWithGoogle(r.idToken)
                                GoogleResult.Cancelled -> vm.onGoogleError(null)
                                is GoogleResult.Failed -> vm.onGoogleError(r.message)
                            }
                        }
                    },
                    enabled = !vm.loading,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) {
                    Text("Continue with Google", style = MaterialTheme.typography.titleMedium)
                }

                TextButton(onClick = vm::toggleMode, enabled = !vm.loading) {
                    Text(if (vm.isCreateMode) "Already have an account? Sign in" else "New here? Create an account")
                }
            }
        }
    }
}

@Composable
private fun OrDivider() {
    androidx.compose.foundation.layout.Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HorizontalDivider(Modifier.weight(1f))
        Text("or", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        HorizontalDivider(Modifier.weight(1f))
    }
}

@Composable
private fun ErrorBanner(message: String) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        androidx.compose.foundation.layout.Row(
            Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.ErrorOutline, contentDescription = "Error")
            Text(message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
