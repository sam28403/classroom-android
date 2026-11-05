package cc.samlab.classroom.ui.avatar

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import cc.samlab.classroom.R
import cc.samlab.classroom.ui.components.PageHeader
import cc.samlab.classroom.ui.components.PageTitle
import cc.samlab.classroom.ui.components.UserAvatar
import cc.samlab.classroom.ui.theme.ClassroomTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import cc.samlab.classroom.data.ClassroomApi
import cc.samlab.classroom.data.MAX_AVATAR_BYTES
import cc.samlab.classroom.data.isSupportedAvatar
import java.nio.ByteBuffer

@Composable
fun AvatarEditorScreen(server: String, avatar: String?, username: String, onBack: () -> Unit,
    api: ClassroomApi, onRefreshUser: suspend () -> Unit) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    var selectedUri by rememberSaveable(server, username) { mutableStateOf<String?>(null) }
    var photoBytes by remember(server, username, selectedUri) { mutableStateOf<ByteArray?>(null) }
    var agreed by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(true) }
    var statusKnown by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    var requestText by remember { mutableStateOf<String?>(null) }
    suspend fun refreshRequests() {
        checking = true
        statusKnown = false
        try {
            val rows = api.avatarRequests()
            val requests = (0 until rows.length()).map {
                rows.optJSONObject(it) ?: throw java.io.IOException(context.getString(R.string.invalid_server_response))
            }.filter { it.optString("kind") == "avatar_change" }
            if (requests.any { it.optLong("id", -1) <= 0 || it.optString("status").isBlank() })
                throw java.io.IOException(context.getString(R.string.invalid_server_response))
            val latest = requests.maxByOrNull { it.optLong("id") }
            pending = requests.any { it.optString("status") !in listOf("approved", "rejected", "cancelled") }
            requestText = latest?.let {
                val label = when(it.optString("status")) {
                    "pending" -> R.string.avatar_status_pending
                    "approved" -> R.string.avatar_status_approved
                    "rejected" -> R.string.avatar_status_rejected
                    "cancelled" -> R.string.avatar_status_cancelled
                    else -> R.string.avatar_status_unknown
                }
                val note = it.optString("review_note").takeUnless { n -> n.isBlank() || n == "null" }
                context.getString(R.string.avatar_latest_request, context.getString(label)) +
                    (note?.let { n -> "\n" + context.getString(R.string.avatar_review_note, n) } ?: "")
            } ?: context.getString(R.string.avatar_no_requests)
            statusKnown = true
            onRefreshUser()
        } catch (e: java.io.IOException) {
            notice = e.message
            if (e is cc.samlab.classroom.data.ApiException && e.status == 401) onRefreshUser()
        } finally { checking = false }
    }
    LaunchedEffect(api) { refreshRequests() }
    val lifecycle = (LocalContext.current as? LifecycleOwner)?.lifecycle
    val refreshOnResume by rememberUpdatedState<() -> Unit> {
        if (!submitting && !checking) scope.launch { refreshRequests() }
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshOnResume()
        }
        lifecycle?.addObserver(observer)
        onDispose { lifecycle?.removeObserver(observer) }
    }
    BackHandler(submitting) { }
    var bitmap by remember(server, username, selectedUri) { mutableStateOf<Bitmap?>(null) }
    var loading by remember(server, username, selectedUri) { mutableStateOf(selectedUri != null) }
    var error by remember(server, username, selectedUri) { mutableStateOf<Int?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        // Cancelling the system picker preserves the current selection.
        if (uri != null) selectedUri = uri.toString()
    }
    LaunchedEffect(selectedUri, server, username) {
        photoBytes = null
        agreed = false
        val uri = selectedUri ?: return@LaunchedEffect
        try {
            val loaded = withContext(Dispatchers.IO) {
                val bytes = context.contentResolver.openInputStream(Uri.parse(uri))?.use { input ->
                    val output = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (true) {
                        val count = input.read(buffer)
                        if (count == -1) break
                        if (output.size() + count > MAX_AVATAR_BYTES) throw PhotoError(R.string.avatar_too_large)
                        output.write(buffer, 0, count)
                    }
                    output.toByteArray()
                } ?: throw PhotoError(R.string.avatar_read_failed)
                if (!isSupportedAvatar(bytes)) throw PhotoError(R.string.avatar_invalid_format)
                val decoded = ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(bytes))) { decoder, info, _ ->
                    if (info.size.width.toLong() * info.size.height > 40_000_000L) {
                        throw PhotoError(R.string.avatar_dimensions_large)
                    }
                    val scale = minOf(1f, 768f / maxOf(info.size.width, info.size.height))
                    decoder.setTargetSize(maxOf(1, (info.size.width * scale).toInt()), maxOf(1, (info.size.height * scale).toInt()))
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
                bytes to decoded
            }
            photoBytes = loaded.first
            bitmap = loaded.second
        } catch (e: PhotoError) {
            error = e.messageId
        } catch (_: java.io.IOException) {
            error = R.string.avatar_read_failed
        } catch (_: SecurityException) {
            error = R.string.avatar_read_failed
        } catch (_: IllegalArgumentException) {
            error = R.string.avatar_read_failed
        } finally {
            loading = false
        }
    }
    AvatarEditorContent(
        preview = bitmap,
        loading = loading,
        error = error,
        hasSelection = selectedUri != null,
        currentAvatar = { UserAvatar(server, avatar, username, size = 144.dp) },
        onPick = { if (!submitting) picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        onClear = { if (!submitting) selectedUri = null },
        onBack = { if (!submitting) onBack() },
        busy = submitting,
        submission = {
            requestText?.let { Text(it) }
            notice?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            TextButton(onClick = { scope.launch { notice = null; refreshRequests() } }, enabled = !checking && !submitting) { Text(stringResource(R.string.avatar_refresh_status)) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = agreed, onCheckedChange = { agreed = it }, enabled = !submitting)
                Text(stringResource(R.string.avatar_consent), Modifier.weight(1f))
            }
            Button(
                enabled = photoBytes != null && !loading && agreed && statusKnown && !pending && !checking && !submitting,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val bytes = photoBytes ?: return@Button
                    submitting = true
                    notice = null
                    scope.launch {
                        try {
                            val id = api.uploadAvatar(bytes)
                            selectedUri = null
                            notice = context.getString(R.string.avatar_submitted, id)
                            pending = true
                        } catch (e: java.io.IOException) {
                            notice = if (e is cc.samlab.classroom.data.ApiException) e.message
                                else context.getString(R.string.avatar_submit_uncertain)
                            if (e is cc.samlab.classroom.data.ApiException && e.status == 401) onRefreshUser()
                        } finally {
                            refreshRequests()
                            submitting = false
                        }
                    }
                },
            ) { Text(stringResource(if (submitting) R.string.avatar_submitting else R.string.avatar_submit)) }
        },
    )
}

private class PhotoError(val messageId: Int) : Exception()

@Composable
internal fun AvatarEditorContent(
    preview: Bitmap?, loading: Boolean, error: Int?, hasSelection: Boolean,
    currentAvatar: @Composable () -> Unit, onPick: () -> Unit, onClear: () -> Unit, onBack: () -> Unit,
    submission: (@Composable () -> Unit)? = null,
    busy: Boolean = false,
) {
    Column(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        PageHeader(onBack)
    Column(
        Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        PageTitle(stringResource(R.string.change_avatar))
        Card(shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Box(Modifier.size(160.dp), contentAlignment = Alignment.Center) {
                        when {
                            loading -> CircularProgressIndicator(Modifier.size(32.dp))
                            preview != null -> Image(preview.asImageBitmap(), stringResource(R.string.avatar_preview),
                                Modifier.size(144.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                            else -> currentAvatar()
                        }
                    }
                }
                Text(stringResource(if (preview != null) R.string.avatar_local_preview else R.string.avatar_current),
                    style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.avatar_preview_note), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = onPick, enabled = !loading && !busy, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(if (hasSelection) R.string.avatar_reselect else R.string.avatar_select))
                }
                if (hasSelection) TextButton(onClick = onClear, enabled = !loading && !busy) {
                    Text(stringResource(R.string.avatar_clear_selection))
                }
            }
        }
        error?.let {
            Text(stringResource(it), color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
        OutlinedCard(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.avatar_photo_guidance), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.avatar_format_guidance), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(stringResource(R.string.avatar_review_flow), style = MaterialTheme.typography.bodyMedium)
        if (submission != null) submission() else {
        Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp)) {
            Text(stringResource(R.string.avatar_submit))
        }
        }
    }
    }
}

@Preview(showBackground = true, heightDp = 1100)
@Composable
private fun AvatarEditorPreview() {
    ClassroomTheme { AvatarEditorContent(null, false, null, false, {}, {}, {}, {}) }
}

@Preview(showBackground = true, heightDp = 1100)
@Composable
private fun AvatarEditorDarkPreview() {
    ClassroomTheme(darkTheme = true) { AvatarEditorContent(null, false, R.string.avatar_invalid_format, true, {}, {}, {}, {}) }
}
