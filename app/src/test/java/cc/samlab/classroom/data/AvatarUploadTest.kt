package cc.samlab.classroom.data

import org.junit.Assert.*
import org.junit.Test

class AvatarUploadTest {
    private val png = byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10, 0, -1)
    private val jpeg = byteArrayOf(-1, -40, -1, 0, -128)

    @Test fun multipartPreservesBinaryFileAndUsesContractField() {
        for ((bytes, type, extension) in listOf(Triple(png, "image/png", "png"),
            Triple(jpeg, "image/jpeg", "jpg"))) {
            val upload = AvatarUpload(bytes)
            val header = ("--${upload.boundary}\r\nContent-Disposition: form-data; name=\"file\"; " +
                "filename=\"avatar.$extension\"\r\nContent-Type: $type\r\n\r\n").toByteArray()
            val tail = "\r\n--${upload.boundary}--\r\n".toByteArray()
            assertArrayEquals(header + bytes + tail, upload.body)
            assertEquals("multipart/form-data; boundary=${upload.boundary}", upload.contentType)
        }
    }

    @Test fun acceptsExactlyFiveMiBAndRejectsLargerFile() {
        val limit = png.copyOf(MAX_AVATAR_BYTES)
        assertTrue(AvatarUpload(limit).body.size > limit.size)
        assertThrows(IllegalArgumentException::class.java) { AvatarUpload(limit.copyOf(limit.size + 1)) }
    }

    @Test fun rejectsEmptyTruncatedAndUnsupportedSignatures() {
        for (bytes in listOf(byteArrayOf(), png.copyOf(7), jpeg.copyOf(2),
            "GIF89a".toByteArray(), "<svg/>".toByteArray())) {
            assertFalse(isSupportedAvatar(bytes))
            assertThrows(IllegalArgumentException::class.java) { AvatarUpload(bytes) }
        }
    }
}
