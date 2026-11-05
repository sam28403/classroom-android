package cc.samlab.classroom.ui.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import cc.samlab.classroom.R
import cc.samlab.classroom.ui.components.PageHeader
import cc.samlab.classroom.ui.components.PageTitle

@Composable
fun LoginScreen(
    hasBackend: Boolean,
    state: LoginState,
    onBack: () -> Unit,
    onSettings: () -> Unit,
    onSuccess: () -> Unit,
) {
    var username by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    LaunchedEffect(state) { state.refreshCaptcha() }
    LaunchedEffect(state.captcha) { code = "" }

    fun submit() {
        if (username.isNotBlank() && password.isNotEmpty() && code.isNotBlank()) {
            state.login(username, password, code) {
                password = ""
                onSuccess()
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .imePadding()
    ) {
        PageHeader(onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        PageTitle(stringResource(R.string.login))
        Column(
            modifier = Modifier.padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            LoginCredentials(
                username = username,
                onUsernameChange = { username = it },
                password = password,
                onPasswordChange = { password = it },
                enabled = !state.busy,
            )
            if (hasBackend) {
                CaptchaField(
                    code = code,
                    onCodeChange = { code = it },
                    image = state.captcha,
                    loading = state.loadingCaptcha,
                    enabled = !state.busy,
                    onRefresh = state::refreshCaptcha,
                    onSubmit = { submit() },
                )
            }
            state.error?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            Button(
                onClick = { submit() },
                enabled = hasBackend && !state.busy && !state.loadingCaptcha &&
                    state.captcha != null && username.isNotBlank() &&
                    password.isNotEmpty() && code.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(18.dp),
            ) {
                Text(
                    stringResource(if (state.busy) R.string.signing_in else R.string.login),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            if (!hasBackend) {
                TextButton(onClick = onSettings, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.configure_backend))
                }
            }
        }
        }
    }
}
