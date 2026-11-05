package cc.samlab.classroom.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import cc.samlab.classroom.R
import cc.samlab.classroom.ui.components.EntryRow
import cc.samlab.classroom.ui.components.UserAvatar
import cc.samlab.classroom.ui.theme.ClassroomTheme

@Composable
fun HomeScreen(
    onLogin: () -> Unit,
    onSettings: () -> Unit,
    username: String? = null,
    avatar: String? = null,
    server: String = "",
    onCourses: () -> Unit = {},
    ongoingCourses: @Composable () -> Unit = {},
) {
    Column(
        modifier = Modifier.fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Image(
                painterResource(R.drawable.picture1),
                contentDescription = null,
                modifier = Modifier.size(44.dp).clip(CircleShape),
            )
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontFamily = FontFamily(Font(R.font.google_sans_rounded_regular)),
                ),
                modifier = Modifier.semantics { heading() },
            )
        }
        EntryRow(
            title = username ?: stringResource(R.string.login),
            icon = R.drawable.ic_person,
            onClick = onLogin,
            leadingContent = if (username == null) null else {
                { UserAvatar(server, avatar, username) }
            },
        )
        ongoingCourses()
        EntryRow(
            title = stringResource(R.string.courses),
            icon = R.drawable.ic_courses,
            onClick = onCourses,
        )
        EntryRow(
            title = stringResource(R.string.settings),
            icon = R.drawable.ic_settings,
            onClick = onSettings,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun HomePreview() {
    ClassroomTheme { HomeScreen({}, {}) }
}
