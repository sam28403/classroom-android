package cc.samlab.classroom.ui.attendance

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import cc.samlab.classroom.R
import cc.samlab.classroom.data.ApiException
import cc.samlab.classroom.data.ClassroomApi
import cc.samlab.classroom.data.MAX_AVATAR_BYTES
import cc.samlab.classroom.ui.components.PageLayout
import cc.samlab.classroom.ui.components.AttendanceEmptyState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttendanceScreen(api: ClassroomApi, onBack: () -> Unit, onAvatar: () -> Unit, onPrivacy: () -> Unit, onLogin: () -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    var face by remember { mutableStateOf<JSONObject?>(null) }
    var tasks by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var history by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var refreshing by remember { mutableStateOf(false) }
    var known by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var needsLogin by remember { mutableStateOf(false) }
    var taskId by rememberSaveable { mutableStateOf<Long?>(null) }
    var capturePath by rememberSaveable { mutableStateOf<String?>(null) }
    var captured by rememberSaveable { mutableStateOf(false) }
    var photo by remember { mutableStateOf<Bitmap?>(null) }
    var bytes by remember { mutableStateOf<ByteArray?>(null) }
    var result by rememberSaveable { mutableStateOf<String?>(null) }
    fun error(e: Exception, fallback: Int = R.string.attendance_failed) {
        if (e is CancellationException) throw e
        if (e is ApiException && (e.status == 401 || e.code == "ANDROID_REQUIRED")) needsLogin = true
        Toast.makeText(context, if (e is ApiException) e.message else resources.getString(fallback),
            Toast.LENGTH_LONG).show()
    }
    suspend fun refresh() {
        refreshing = true
        known = false
        try {
            val nextFace = api.faceStatus()
            val nextTasks = rows(api.attendanceTasks())
            val nextHistory = rows(api.attendanceHistory())
            face = nextFace
            tasks = nextTasks
            history = nextHistory
            known = true
        } catch (e: Exception) { error(e) }
        finally { refreshing = false }
    }
    fun clearPhoto() {
        capturePath?.let { File(it).delete() }
        capturePath = null
        captured = false
        taskId = null
        photo = null
        bytes = null
    }
    val latestCapturePath by rememberUpdatedState(capturePath)
    DisposableEffect(Unit) {
        onDispose {
            if ((context as? android.app.Activity)?.isChangingConfigurations != true)
                latestCapturePath?.let { File(it).delete() }
        }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        captured = success
        if (!success) clearPhoto()
    }
    LaunchedEffect(api) {
        withContext(Dispatchers.IO) { cleanExpiredCaptures(File(context.cacheDir, "attendance"), capturePath) }
        refresh()
    }
    val lifecycle = (context as? LifecycleOwner)?.lifecycle
    val resume by rememberUpdatedState<() -> Unit> {
        if (!busy && !refreshing) scope.launch { refresh() }
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) resume() }
        lifecycle?.addObserver(observer)
        onDispose { lifecycle?.removeObserver(observer) }
    }
    LaunchedEffect(captured, capturePath) {
        photo = null
        bytes = null
        if (!captured) return@LaunchedEffect
        busy = true
        try {
            val loaded = withContext(Dispatchers.IO) {
                val file = File(requireNotNull(capturePath))
                if (file.length() !in 1..40L * 1024 * 1024) throw IOException()
                val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
                    if (info.size.width.toLong() * info.size.height > 40_000_000) throw IOException()
                    val scale = minOf(1f, 1280f / maxOf(info.size.width, info.size.height))
                    decoder.setTargetSize(maxOf(1, (info.size.width * scale).toInt()),
                        maxOf(1, (info.size.height * scale).toInt()))
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
                val output = java.io.ByteArrayOutputStream()
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output) || output.size() > MAX_AVATAR_BYTES)
                    throw IOException()
                bitmap to output.toByteArray()
            }
            photo = loaded.first
            bytes = loaded.second
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            clearPhoto()
            error(e, R.string.avatar_read_failed)
        }
        finally { busy = false }
    }
    BackHandler(busy) {}
    PageLayout(stringResource(R.string.attendance), { if (!busy) onBack() },
        action = { IconButton(enabled = !busy && !refreshing, onClick = { scope.launch { refresh() } }) {
            Icon(painterResource(R.drawable.ic_refresh), contentDescription = stringResource(R.string.attendance_refresh))
        } }) {
        PullToRefreshBox(isRefreshing = refreshing, onRefresh = { if (!busy && !refreshing) scope.launch { refresh() } }, modifier = Modifier.weight(1f)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)) {
            if (needsLogin) Button(onClick = onLogin, enabled = !busy) { Text(stringResource(R.string.attendance_login)) }
            if (!known) Text(stringResource(R.string.attendance_unknown))
            face?.let { status ->
                if (!status.optBoolean("approvedAvatar")) {
                    Surface(color = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(stringResource(R.string.attendance_avatar_required), style = MaterialTheme.typography.bodyLarge)
                            TextButton(onClick = onAvatar, enabled = !busy) { Text(stringResource(R.string.change_avatar), color = MaterialTheme.colorScheme.onErrorContainer) }
                        }
                    }
                }
                if (!status.optBoolean("consent")) {
                    Text(stringResource(R.string.privacy_face_not_granted))
                    TextButton(onClick = onPrivacy, enabled = !busy) { Text(stringResource(R.string.privacy_face_settings)) }
                } else {
                    Text(stringResource(if (status.optBoolean("ready")) R.string.attendance_ready else R.string.attendance_sync))
                }
            }
            result?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
            Text(stringResource(R.string.attendance_available), style = MaterialTheme.typography.titleLarge)
            if (known && tasks.isEmpty()) AttendanceEmptyState(stringResource(R.string.attendance_empty), R.drawable.ic_attendance_task)
            for (task in tasks) {
                val id = task.optLong("id")
                val record = history.find { it.optLong("task_id") == id }
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(task.optString("name"), style = MaterialTheme.typography.titleMedium)
                        Text(resources.getString(R.string.attendance_deadline, displayTime(task.optString("end_at"))))
                        if (record != null) Text(stringResource(statusLabel(record.optString("status"))))
                        Button(enabled = known && !busy && !refreshing && record == null &&
                            face?.optBoolean("consent") == true && face?.optBoolean("approvedAvatar") == true,
                            onClick = {
                                clearPhoto()
                                result = null
                                taskId = id
                                try {
                                    val directory = File(context.cacheDir, "attendance").apply { mkdirs() }
                                    val file = File.createTempFile("capture-", ".jpg", directory)
                                    capturePath = file.path
                                    camera.launch(FileProvider.getUriForFile(context, context.packageName + ".camera", file))
                                } catch (e: Exception) { clearPhoto(); error(e, R.string.attendance_camera_failed) }
                            }) { Text(stringResource(R.string.attendance_capture)) }
                    }
                }
            }
            photo?.let { bitmap ->
                Text(stringResource(R.string.attendance_photo))
                Image(bitmap.asImageBitmap(), stringResource(R.string.attendance_photo),
                    Modifier.fillMaxWidth().heightIn(max = 360.dp))
                Button(enabled = !busy && !refreshing && known && bytes != null &&
                    face?.optBoolean("consent") == true && face?.optBoolean("approvedAvatar") == true &&
                    tasks.any { it.optLong("id") == taskId } && history.none { it.optLong("task_id") == taskId },
                    onClick = {
                        val image = bytes ?: return@Button
                        val id = taskId ?: return@Button
                        busy = true
                        scope.launch {
                            try {
                                val response = api.checkIn(id, image)
                                result = resources.getString(statusLabel(response.optString("status")))
                                clearPhoto()
                            } catch (e: Exception) {
                                error(e)
                                if (e is ApiException && e.code in setOf("AVATAR_CHANGED", "TASK_INACTIVE", "DUPLICATE_CHECK_IN")) clearPhoto()
                                if (e !is ApiException) result = resources.getString(R.string.attendance_uncertain)
                            } finally {
                                try { refresh() } finally { busy = false }
                            }
                        }
                    }) { Text(stringResource(if (busy) R.string.attendance_uploading else R.string.attendance_submit)) }
                TextButton(onClick = { clearPhoto() }, enabled = !busy) { Text(stringResource(R.string.action_cancel)) }
            }
            Text(stringResource(R.string.attendance_history), style = MaterialTheme.typography.titleLarge)
            if (known && history.isEmpty()) AttendanceEmptyState(stringResource(R.string.attendance_no_history), R.drawable.ic_attendance_history)
            history.forEach { record ->
                ListItem(headlineContent = { Text(record.optString("name")) },
                    supportingContent = { Text(displayTime(record.optString("checked_at"))) },
                    trailingContent = { Text(stringResource(statusLabel(record.optString("status")))) })
            }
        }
        }
    }
}

private fun rows(array: JSONArray): List<JSONObject> = (0 until array.length()).map { array.getJSONObject(it) }
private fun displayTime(value: String): String = runCatching {
    DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(ZoneId.of("Asia/Shanghai")).format(Instant.parse(value))
}.getOrDefault(value)
private fun statusLabel(status: String): Int = when (status) {
    "present" -> R.string.attendance_present
    "late" -> R.string.attendance_late
    "exception" -> R.string.attendance_exception
    "absent" -> R.string.attendance_absent
    "leave" -> R.string.attendance_leave
    "manual" -> R.string.attendance_manual
    else -> R.string.attendance_record_unknown
}
