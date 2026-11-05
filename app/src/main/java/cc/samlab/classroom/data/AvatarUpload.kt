package cc.samlab.classroom.data

import java.io.ByteArrayOutputStream
import java.util.UUID

internal const val MAX_AVATAR_BYTES = 5 * 1024 * 1024

internal fun isSupportedAvatar(bytes: ByteArray): Boolean =
    avatarMimeType(bytes) != null

private fun avatarMimeType(bytes: ByteArray): String? {
    val png = byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10)
    return when {
        bytes.size >= png.size && bytes.copyOfRange(0, png.size).contentEquals(png) -> "image/png"
        bytes.size >= 3 && bytes[0] == 255.toByte() && bytes[1] == 216.toByte() &&
            bytes[2] == 255.toByte() -> "image/jpeg"
        else -> null
    }
}

internal class AvatarUpload(bytes: ByteArray) {
    val boundary = "Classroom-${UUID.randomUUID()}"
    val contentType = "multipart/form-data; boundary=$boundary"
    val body: ByteArray

    init {
        require(bytes.isNotEmpty() && bytes.size <= MAX_AVATAR_BYTES)
        val type = requireNotNull(avatarMimeType(bytes))
        val extension = if (type == "image/png") "png" else "jpg"
        body = ByteArrayOutputStream().apply {
            write(("--$boundary\r\nContent-Disposition: form-data; name=\"file\"; " +
                "filename=\"avatar.$extension\"\r\nContent-Type: $type\r\n\r\n").toByteArray(Charsets.UTF_8))
            write(bytes)
            write("\r\n--$boundary--\r\n".toByteArray(Charsets.UTF_8))
        }.toByteArray()
    }
}
