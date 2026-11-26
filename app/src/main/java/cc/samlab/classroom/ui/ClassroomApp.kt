package cc.samlab.classroom.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.core.content.ContextCompat
import com.google.firebase.FirebaseApp
import cc.samlab.classroom.notifications.AttendanceNotifications
import cc.samlab.classroom.data.BackendSettings
import cc.samlab.classroom.data.ClassroomApi
import cc.samlab.classroom.ui.home.HomeScreen
import cc.samlab.classroom.ui.login.StudentOnlyDialog
import cc.samlab.classroom.ui.login.AccountScreen
import cc.samlab.classroom.ui.login.LoginScreen
import cc.samlab.classroom.ui.login.LoginState
import cc.samlab.classroom.ui.settings.SettingsScreen
import cc.samlab.classroom.ui.settings.PrivacyScreen
import cc.samlab.classroom.ui.avatar.AvatarEditorScreen
import cc.samlab.classroom.ui.attendance.AttendanceScreen
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.time.Instant
import cc.samlab.classroom.ui.courses.CourseState
import cc.samlab.classroom.ui.courses.CourseCard
import cc.samlab.classroom.ui.courses.CoursesScreen
import cc.samlab.classroom.ui.courses.CourseDetailsScreen

private enum class Destination { Home, Settings, Privacy, Login, Courses, CourseDetails, AvatarEditor, Attendance }

@Composable
fun ClassroomApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = remember { BackendSettings(context.applicationContext) }
    var address by remember { mutableStateOf(settings.address) }
    var destination by rememberSaveable { mutableStateOf(Destination.Home) }
    var selectedCourseId by rememberSaveable { mutableStateOf<Long?>(null) }
    var lastStudentId by rememberSaveable { mutableStateOf<Long?>(null) }
    val api = remember(address) {
        address.takeIf { it.isNotEmpty() }?.let { ClassroomApi(context.applicationContext, it) }
    }
    val auth = remember(api) { LoginState(context.applicationContext, api, scope) }
    LaunchedEffect(auth.user?.id) {
        if (auth.user?.role == "student") lastStudentId = auth.user?.id
    }
    val notificationPrefs = remember { context.getSharedPreferences("notifications", Context.MODE_PRIVATE) }
    var askNotifications by remember { mutableStateOf(false) }
    val requestNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
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
    LaunchedEffect(auth.user?.id, address) {
        if (auth.user?.role == "student" && FirebaseApp.getApps(context).isNotEmpty()) {
            AttendanceNotifications.prepare(context)
            AttendanceNotifications.register(context)
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
                !notificationPrefs.getBoolean("permission_asked", false)) askNotifications = true
        }
    }
    DisposableEffect(auth, courses, context) {
        val lifecycle = (context as? LifecycleOwner)?.lifecycle
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && auth.user != null) {
                scope.launch {
                    auth.restore()
                    if (auth.user?.role == "student") courses.refresh()
                    AttendanceNotifications.register(context)
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
                Destination.Privacy -> Destination.Settings
                else -> Destination.Home
            }
        }
    }
    BackHandler(destination != Destination.Home) { goBack() }

    if (auth.studentOnlyDialog) StudentOnlyDialog(auth::dismissStudentOnlyDialog)
    if (askNotifications) AlertDialog(
        onDismissRequest = { askNotifications = false; notificationPrefs.edit().putBoolean("permission_asked", true).apply() },
        title = { Text("接收考勤通知") },
        text = { Text("开启通知后，教师发起考勤时可以及时提醒你。") },
        confirmButton = { TextButton(onClick = {
            askNotifications = false
            notificationPrefs.edit().putBoolean("permission_asked", true).apply()
            if (Build.VERSION.SDK_INT >= 33) requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }) { Text("开启通知") } },
        dismissButton = { TextButton(onClick = {
            askNotifications = false
            notificationPrefs.edit().putBoolean("permission_asked", true).apply()
        }) { Text("暂不开启") } },
    )
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
                    onAttendance = {
                        destination = if (auth.user?.role == "student") Destination.Attendance else Destination.Login
                    },
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
                Destination.Attendance -> if (api != null && (auth.user?.role == "student" || (!auth.initialRestoreComplete && lastStudentId != null))) {
                    androidx.compose.runtime.key(address, auth.user?.id ?: lastStudentId) {
                        AttendanceScreen(api, goHome,
                            onAvatar = { destination = Destination.AvatarEditor },
                            onPrivacy = { destination = Destination.Privacy },
                            onLogin = { auth.logout { destination = Destination.Login } })
                    }
                } else if (auth.initialRestoreComplete) {
                    LaunchedEffect(Unit) { destination = Destination.Login }
                }
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
                    onPrivacy = { destination = Destination.Privacy },
                )
                Destination.Privacy -> PrivacyScreen(api = if (auth.user?.role == "student") api else null,
                    onBack = { destination = Destination.Settings }, onLogin = { destination = Destination.Login })
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
