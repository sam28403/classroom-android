package cc.samlab.classroom.ui.settings

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cc.samlab.classroom.R
import cc.samlab.classroom.data.ApiException
import cc.samlab.classroom.data.ClassroomApi
import cc.samlab.classroom.ui.components.PageLayout
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun PrivacyScreen(api: ClassroomApi?, onBack: () -> Unit, onLogin: () -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    var consent by remember(api) { mutableStateOf<Boolean?>(null) }
    var busy by remember { mutableStateOf(false) }
    fun showError(error: Exception) {
        if (error is CancellationException) throw error
        Toast.makeText(context, if (error is ApiException) error.message else resources.getString(R.string.attendance_failed), Toast.LENGTH_LONG).show()
    }
    LaunchedEffect(api) {
        if (api != null) try { consent = api.faceStatus().optBoolean("consent") } catch (error: Exception) { showError(error) }
    }
    PageLayout(stringResource(R.string.privacy), onBack) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text(stringResource(R.string.privacy_face_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.attendance_consent_notice), style = MaterialTheme.typography.bodyLarge)
            if (api == null) {
                Text(stringResource(R.string.privacy_face_login))
                TextButton(onClick = onLogin) { Text(stringResource(R.string.attendance_login)) }
            } else if (consent != null) {
                Text(stringResource(if (consent == true) R.string.privacy_face_granted else R.string.privacy_face_not_granted))
                Button(enabled = !busy, onClick = {
                    val next = consent != true
                    busy = true
                    scope.launch {
                        try { api.faceConsent(next); consent = next } catch (error: Exception) { showError(error) }
                        finally { busy = false }
                    }
                }) { Text(stringResource(if (consent == true) R.string.attendance_withdraw else R.string.attendance_agree)) }
            } else {
                TextButton(enabled = !busy, onClick = {
                    busy = true
                    scope.launch { try { consent = api.faceStatus().optBoolean("consent") } catch (error: Exception) { showError(error) } finally { busy = false } }
                }) { Text(stringResource(R.string.retry)) }
            }
        }
    }
}
