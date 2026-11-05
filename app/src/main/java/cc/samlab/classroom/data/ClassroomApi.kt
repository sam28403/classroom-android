package cc.samlab.classroom.data

import android.content.Context
import cc.samlab.classroom.R
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class ClassroomUser(
    val id: Long,
    val username: String,
    val avatar: String?,
    val role: String,
    val name: String?,
    val studentNumber: String?,
    val className: String?,
)

class ApiException(val status: Int, message: String, val code: String? = null) : IOException(message)

class ClassroomApi(private val context: Context, private val server: String) {
    private val cookies = SessionCookies(context, server)
    private val mutex = Mutex()

    suspend fun currentUser(): ClassroomUser? = try {
        user(request("/auth/me"))
    } catch (error: ApiException) {
        if (error.status != 401) throw error
        cookies.clear()
        null
    }

    suspend fun courseSchedule(): CourseSchedule = CourseSchedule.parse(request("/schedule"))

    suspend fun captcha(): String = request("/captcha").getString("image")

    suspend fun avatarRequests(): org.json.JSONArray = request("/requests").optJSONArray("items")
        ?: throw IOException(context.getString(R.string.invalid_server_response))

    suspend fun uploadAvatar(bytes: ByteArray): Long {
        val upload = AvatarUpload(bytes)
        val result = request("/avatar-requests", raw = upload.body,
            contentType = upload.contentType, expectedStatus = 202)
        return result.optLong("requestId", -1).takeIf { it > 0 }
            ?: throw IOException(context.getString(R.string.invalid_server_response))
    }

    suspend fun login(username: String, password: String, code: String): ClassroomUser =
        user(request("/android/auth/login", JSONObject().apply {
            put("username", username.trim())
            put("password", password)
            put("code", code.trim())
        }))

    suspend fun logout() {
        request("/auth/logout", JSONObject())
        cookies.clear()
    }

    private suspend fun user(result: JSONObject): ClassroomUser {
        val data = result.getJSONObject("user")
        val profile = request("/profile").optJSONObject("profile")
        return ClassroomUser(
            id = data.getLong("id"),
            role = data.getString("role"),
            name = profile?.optString("name")?.takeIf { it.isNotBlank() },
            studentNumber = profile?.optString("student_no")?.takeIf { it.isNotBlank() },
            className = profile?.optString("class_name")?.takeIf { it.isNotBlank() },
            username = data.getString("username"),
            avatar = data.optString("avatar").takeUnless { it.isEmpty() || it == "null" },
        )
    }

    private suspend fun request(path: String, body: JSONObject? = null, raw: ByteArray? = null,
        contentType: String? = null, expectedStatus: Int? = null): JSONObject =
        mutex.withLock {
            withContext(Dispatchers.IO) {
                val uri = URI("$server/api$path")
                val connection = uri.toURL().openConnection() as HttpURLConnection
                try {
                    connection.connectTimeout = 10_000
                    connection.readTimeout = if (raw != null) 60_000 else 15_000
                    connection.instanceFollowRedirects = false
                    connection.requestMethod = if (body == null && raw == null) "GET" else "POST"
                    connection.setRequestProperty("Accept", "application/json")
                    cookies.headers(uri).forEach { (name, values) ->
                        connection.setRequestProperty(name, values.joinToString("; "))
                    }
                    if (body != null || raw != null) {
                        connection.doOutput = true
                        connection.setRequestProperty("Content-Type", contentType ?: "application/json; charset=utf-8")
                        connection.outputStream.use { it.write(raw ?: body.toString().toByteArray(Charsets.UTF_8)) }
                    }
                    val status = connection.responseCode
                    cookies.update(uri, connection.headerFields)
                    val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                    val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                    val result = try {
                        JSONObject(text)
                    } catch (_: Exception) {
                        throw ApiException(status, context.getString(R.string.invalid_server_response))
                    }
                    if (status !in 200..299) {
                        throw ApiException(
                            status,
                            result.optString("message").ifBlank {
                                context.getString(R.string.request_failed)
                            },
                            result.optString("code"),
                        )
                    }
                    if (expectedStatus != null && status != expectedStatus) {
                        throw IOException(context.getString(R.string.invalid_server_response))
                    }
                    result
                } finally {
                    connection.disconnect()
                }
            }
        }
}
