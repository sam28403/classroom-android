package cc.samlab.classroom.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cc.samlab.classroom.R
import cc.samlab.classroom.ui.components.EntryRow
import cc.samlab.classroom.ui.components.PageHeader
import cc.samlab.classroom.ui.components.PageTitle

@Composable
fun SettingsScreen(
    address: String,
    onBack: () -> Unit,
    onSaveAddress: (String) -> Unit,
) {
    var showAddressDialog by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        PageHeader(onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        PageTitle(stringResource(R.string.settings))
        EntryRow(
            title = stringResource(R.string.backend_address),
            subtitle = address.ifEmpty { stringResource(R.string.not_configured) },
            icon = R.drawable.ic_server,
            onClick = { showAddressDialog = true },
        )
        }
    }
    if (showAddressDialog) {
        BackendAddressDialog(
            address = address,
            onDismiss = { showAddressDialog = false },
            onSave = {
                onSaveAddress(it)
                showAddressDialog = false
            },
        )
    }
}
