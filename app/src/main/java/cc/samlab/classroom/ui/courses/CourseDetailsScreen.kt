package cc.samlab.classroom.ui.courses

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cc.samlab.classroom.R
import cc.samlab.classroom.ui.components.PageHeader
import cc.samlab.classroom.ui.components.PageTitle

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CourseDetailsScreen(
    state: CourseState,
    courseId: Long?,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
) {
    val course = state.schedule.courses.find { it.id == courseId }
    val sessions = state.schedule.sessions.filter { it.courseId == courseId }
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        PageHeader(onBack) { CourseRefreshButton(!state.loading, onRefresh) }
    LazyColumn(
        Modifier.weight(1f).padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            PageTitle(stringResource(R.string.course_details))
        }
        when {
            state.loading -> item { LoadingIndicator() }
            state.failed -> item {
                Text(stringResource(R.string.courses_failed))
                TextButton(onClick = onRefresh) { Text(stringResource(R.string.retry)) }
            }
            course == null -> item { Text(stringResource(R.string.course_unavailable)) }
            else -> {
                item {
                    Column(
                        Modifier.padding(horizontal = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(course.title, style = MaterialTheme.typography.headlineSmall)
                        Text(course.englishName.ifBlank { stringResource(R.string.course_english_missing) })
                        Text(course.className)
                        Text(stringResource(R.string.course_teacher, course.teacher))
                        Text(course.term)
                        Text(stringResource(R.string.beijing_time),
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                item {
                    Text(stringResource(R.string.course_schedule),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp))
                }
                if (sessions.isEmpty()) {
                    item { Text(stringResource(R.string.course_sessions_empty)) }
                } else {
                    items(sessions, key = { it.id }) { CourseSessionCard(it) }
                }
            }
        }
    }
    }
}
