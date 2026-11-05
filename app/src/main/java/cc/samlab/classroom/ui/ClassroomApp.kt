package cc.samlab.classroom.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import cc.samlab.classroom.data.BackendSettings
import cc.samlab.classroom.data.ClassroomApi
import cc.samlab.classroom.ui.home.HomeScreen
import cc.samlab.classroom.ui.login.StudentOnlyDialog
import cc.samlab.classroom.ui.login.AccountScreen
import cc.samlab.classroom.ui.login.LoginScreen
import cc.samlab.classroom.ui.login.LoginState
import cc.samlab.classroom.ui.settings.SettingsScreen
import cc.samlab.classroom.ui.avatar.AvatarEditorScreen
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.time.Instant
import cc.samlab.classroom.ui.courses.CourseState
import cc.samlab.classroom.ui.courses.CourseCard
import cc.samlab.classroom.ui.courses.CoursesScreen
import cc.samlab.classroom.ui.courses.CourseDetailsScreen

private enum class Destination { Home, Settings, Login, Courses, CourseDetails, AvatarEditor }

@Composable
fun ClassroomApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = remember { BackendSettings(context.applicationContext) }
    var address by remember { mutableStateOf(settings.address) }
    var destination by rememberSaveable { mutableStateOf(Destination.Home) }
    var selectedCourseId by rememberSaveable { mutableStateOf<Long?>(null) }
    val api = remember(address) {
        address.takeIf { it.isNotEmpty() }?.let { ClassroomApi(context.applicationContext, it) }
    }
    val auth = remember(api) { LoginState(context.applicationContext, api, scope) }
    val courses = remember(api, auth.user?.id) { CourseState(api) }
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Instant.now()
            delay(1_000)
        }
    }
    LaunchedEffect(courses, destination) {
        if (auth.user?.role == "student") courses.refresh()
    }
    val goHome = { if (!auth.busy) destination = Destination.Home }

    LaunchedEffect(auth, destination) {
        if (destination == Destination.Home || auth.user == null) auth.restore()
    }
    DisposableEffect(auth, courses, context) {
        val lifecycle = (context as? LifecycleOwner)?.lifecycle
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && auth.user != null) {
                scope.launch {
                    auth.restore()
                    if (auth.user?.role == "student") courses.refresh()
                }
            }
        }
        lifecycle?.addObserver(observer)
        onDispose { lifecycle?.removeObserver(observer) }
    }
    val goBack = {
        if (!auth.busy) {
            destination = when (destination) {
                Destination.CourseDetails -> Destination.Courses
                Destination.AvatarEditor -> Destination.Login
                else -> Destination.Home
            }
        }
    }
    BackHandler(destination != Destination.Home) { goBack() }

    if (auth.studentOnlyDialog) StudentOnlyDialog(auth::dismissStudentOnlyDialog)
    Surface(Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = destination,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "page",
        ) { page ->
            when (page) {
                Destination.Home -> HomeScreen(
                    onLogin = { destination = Destination.Login },
                    onSettings = { destination = Destination.Settings },
                    username = auth.user?.username,
                    avatar = auth.user?.avatar,
                    server = address,
                    onCourses = {
                        destination = if (auth.user?.role == "student") Destination.Courses
                            else Destination.Login
                    },
                    ongoingCourses = {
                        if (auth.user?.role == "student") {
                            courses.schedule.sessions.filter { it.isOngoing(now) }.forEach { session ->
                                courses.schedule.courses.find { it.id == session.courseId }?.let {
                                    CourseCard(it, session, ongoing = true)
                                }
                            }
                        }
                    },
                )
                Destination.Courses -> CoursesScreen(
                    state = courses,
                    onBack = goHome,
                    onCourse = {
                        selectedCourseId = it.id
                        destination = Destination.CourseDetails
                    },
                    onRefresh = { scope.launch { courses.refresh() } },
                )
                Destination.CourseDetails -> CourseDetailsScreen(
                    state = courses,
                    courseId = selectedCourseId,
                    onBack = goBack,
                    onRefresh = { scope.launch { courses.refresh() } },
                )
                Destination.Settings -> SettingsScreen(
                    address = address,
                    onBack = goHome,
                    onSaveAddress = {
                        settings.address = it
                        address = it
                    },
                )
                Destination.AvatarEditor -> if (auth.user != null) {
                    androidx.compose.runtime.key(address, auth.user!!.id) {
                        AvatarEditorScreen(address, auth.user?.avatar, auth.user!!.username, goBack,
                            api = requireNotNull(api), onRefreshUser = { auth.restore() })
                    }
                } else {
                    LaunchedEffect(Unit) { destination = Destination.Login }
                }
                Destination.Login -> if (auth.user != null) {
                    AccountScreen(auth, goHome, onChangeAvatar = { destination = Destination.AvatarEditor })
                } else {
                    LoginScreen(
                        hasBackend = api != null,
                        state = auth,
                        onBack = goHome,
                        onSettings = { destination = Destination.Settings },
                        onSuccess = { destination = Destination.Home },
                    )
                }
            }
        }
    }
}
