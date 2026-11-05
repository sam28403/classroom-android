package cc.samlab.classroom.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import java.net.HttpURLConnection
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal fun resolveAvatarUri(server: String, avatar: String): URI? = runCatching {
    URI("${server.trimEnd('/')}/").resolve(avatar.trim()).takeIf {
        it.scheme in setOf("http", "https") && !it.host.isNullOrBlank() && it.rawUserInfo == null
    }
}.getOrNull()

internal fun sameAvatarOrigin(first: URI, second: URI): Boolean {
    fun port(uri: URI) = if (uri.port != -1) uri.port else if (uri.scheme == "https") 443 else 80
    return first.scheme.equals(second.scheme, true) &&
        first.host.equals(second.host, true) && port(first) == port(second)
}

internal suspend fun loadAvatar(context: Context, server: String, avatar: String): Bitmap? =
    withContext(Dispatchers.IO) {
        try {
            val limit = 5 * 1024 * 1024
            val source = avatar.trim()
            val bytes = if (source.startsWith("data:image/")) {
                val comma = source.indexOf(',')
                if (comma < 0 || !source.substring(0, comma).endsWith(";base64") ||
                    source.length > limit * 4 / 3 + 1024) return@withContext null
                Base64.decode(source.substring(comma + 1), Base64.DEFAULT)
            } else {
                val uri = resolveAvatarUri(server, source) ?: return@withContext null
                val connection = uri.toURL().openConnection() as HttpURLConnection
                try {
                    connection.connectTimeout = 10_000
                    connection.readTimeout = 10_000
                    connection.instanceFollowRedirects = false
                    if (sameAvatarOrigin(URI(server), uri)) {
                        SessionCookies(context, server).headers(uri).forEach { (name, values) ->
                            connection.setRequestProperty(name, values.joinToString("; "))
                        }
                    }
                    if (connection.responseCode != 200 || connection.contentLength > limit) {
                        return@withContext null
                    }
                    connection.inputStream.use { input ->
                        val output = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (true) {
                            val count = input.read(buffer)
                            if (count == -1) break
                            if (output.size() + count > limit) return@withContext null
                            output.write(buffer, 0, count)
                        }
                        output.toByteArray()
                    }
                } finally {
                    connection.disconnect()
                }
            }
            if (bytes.size > limit) return@withContext null
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            if (options.outWidth <= 0 || options.outHeight <= 0) return@withContext null
            options.inJustDecodeBounds = false
            options.inSampleSize = 1
            while (maxOf(options.outWidth, options.outHeight) / options.inSampleSize > 512) {
                options.inSampleSize *= 2
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        } catch (_: java.io.IOException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }
