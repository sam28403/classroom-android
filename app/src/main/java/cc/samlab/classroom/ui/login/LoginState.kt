package cc.samlab.classroom.ui.login

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cc.samlab.classroom.R
import cc.samlab.classroom.data.ApiException
import cc.samlab.classroom.data.ClassroomApi
import cc.samlab.classroom.data.ClassroomUser
import cc.samlab.classroom.data.decodeCaptcha
import cc.samlab.classroom.notifications.AttendanceReminderService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LoginState(
    private val context: Context,
    private val api: ClassroomApi?,
    private val scope: CoroutineScope,
) {
    var user by mutableStateOf<ClassroomUser?>(null)
        private set
    var initialRestoreComplete by mutableStateOf(api == null)
        private set
    var captcha by mutableStateOf<Bitmap?>(null)
        private set
    var busy by mutableStateOf(false)
        private set
    var loadingCaptcha by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var studentOnlyDialog by mutableStateOf(false)
        private set

    fun dismissStudentOnlyDialog() { studentOnlyDialog = false }

    private var revision = 0

    suspend fun restore() {
        if (api == null) return
        val started = revision
        try {
            val restored = api.currentUser()
            if (started == revision) {
                if (restored != null && restored.role != "student") {
                    studentOnlyDialog = true
                    user = null
                    api.logout()
                } else user = restored
                initialRestoreComplete = true
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // A transient network failure does not sign out an authenticated user.
        }
    }

    fun refreshCaptcha() {
        if (api == null || busy || loadingCaptcha) return
        scope.launch {
            error = null
            loadCaptcha()
        }
    }

    private suspend fun loadCaptcha() {
        val client = api ?: return
        loadingCaptcha = true
        captcha = null
        try {
            val data = client.captcha()
            captcha = withContext(Dispatchers.Default) { decodeCaptcha(data) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            if (error == null) error = message(failure)
        } finally {
            loadingCaptcha = false
        }
    }

    fun login(username: String, password: String, code: String, onSuccess: () -> Unit) {
        val client = api ?: return
        if (busy || loadingCaptcha || captcha == null) return
        revision++
        scope.launch {
            busy = true
            error = null
            try {
                val signedIn = client.login(username, password, code)
                if (signedIn.role != "student") {
                    studentOnlyDialog = true
                    client.logout()
                    loadCaptcha()
                } else {
                    user = signedIn
                    initialRestoreComplete = true
                    captcha = null
                    onSuccess()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (failure is ApiException && failure.code == "ANDROID_STUDENT_ONLY") {
                    studentOnlyDialog = true
                } else error = message(failure)
                loadCaptcha()
            } finally {
                busy = false
            }
        }
    }

    fun logout(onSuccess: () -> Unit) {
        val client = api ?: return
        if (busy) return
        revision++
        scope.launch {
            busy = true
            error = null
            try {
                client.logout()
                AttendanceReminderService.stop(context)
                user = null
                initialRestoreComplete = true
                busy = false
                onSuccess()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                error = message(failure)
            } finally {
                busy = false
            }
        }
    }

    private fun message(failure: Exception): String =
        if (failure is ApiException) failure.message.orEmpty()
        else context.getString(R.string.connection_failed)
}
