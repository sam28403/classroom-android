package cc.samlab.classroom.ui.courses

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import cc.samlab.classroom.R
import cc.samlab.classroom.ui.components.PageHeader
import cc.samlab.classroom.ui.components.PageTitle
import cc.samlab.classroom.data.Course
import androidx.compose.material3.Card

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CoursesScreen(
    state: CourseState,
    onBack: () -> Unit,
    onCourse: (Course) -> Unit,
    onRefresh: () -> Unit,
) {
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        PageHeader(onBack) { CourseRefreshButton(!state.loading, onRefresh) }
    LazyColumn(
        Modifier.weight(1f).padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            PageTitle(stringResource(R.string.courses))
        }
        when {
            state.loading -> item {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    LoadingIndicator()
                }
            }
            state.failed -> item {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.courses_failed))
                    TextButton(onClick = onRefresh) { Text(stringResource(R.string.retry)) }
                }
            }
            state.schedule.courses.isEmpty() -> item {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    LoadingIndicator(Modifier.size(96.dp).clearAndSetSemantics {})
                    Text(stringResource(R.string.courses_empty),
                        style = MaterialTheme.typography.titleLarge)
                }
            }
            else -> {
                items(state.schedule.courses, key = { it.id }) { course ->
                    Card(
                        onClick = { onCourse(course) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.extraLarge,
                    ) {
                        Text(
                            course.title,
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(20.dp),
                        )
                    }
                }
            }
        }
    }
    }
}
