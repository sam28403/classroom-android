package cc.samlab.classroom.ui.login

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Card
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cc.samlab.classroom.R
import cc.samlab.classroom.ui.components.PageHeader
import cc.samlab.classroom.ui.components.PageTitle
import cc.samlab.classroom.ui.components.EntryRow

@Composable
fun AccountScreen(state: LoginState, onBack: () -> Unit, onChangeAvatar: () -> Unit = {}) {
    Column(
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        PageHeader(onBack)
        Column(
        modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
        PageTitle(stringResource(R.string.account))
        Text(
            state.user?.username.orEmpty(),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        state.user?.let { user ->
            Card(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(stringResource(R.string.user_role, when (user.role) {
                        "student" -> stringResource(R.string.student)
                        "teacher" -> stringResource(R.string.teacher)
                        else -> stringResource(R.string.administrator)
                    }))
                    user.name?.let { Text(stringResource(R.string.student_name, it)) }
                    user.studentNumber?.let { Text(stringResource(R.string.student_number, it)) }
                    if (user.role == "student") {
                        Text(stringResource(R.string.student_class,
                            user.className ?: stringResource(R.string.not_assigned)))
                    }
                }
            }
        }
        EntryRow(
            title = stringResource(R.string.change_avatar),
            icon = R.drawable.ic_person,
            subtitle = stringResource(R.string.avatar_entry_note),
            onClick = onChangeAvatar,
        )
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(
            onClick = { state.logout(onBack) },
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(if (state.busy) R.string.signing_out else R.string.logout))
        }
        }
    }
}
