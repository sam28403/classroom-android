package cc.samlab.classroom

import android.graphics.Bitmap
import android.util.Base64
import androidx.test.platform.app.InstrumentationRegistry
import cc.samlab.classroom.data.loadAvatar
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class AvatarDecodeTest {
    @Test fun decodesAndDownsamplesRealImage() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val original = Bitmap.createBitmap(1024, 1024, Bitmap.Config.ARGB_8888)
        original.eraseColor(android.graphics.Color.RED)
        val output = ByteArrayOutputStream()
        original.compress(Bitmap.CompressFormat.PNG, 100, output)
        original.recycle()
        val avatar = "data:image/png;base64," + Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
        val bitmap = loadAvatar(context, "https://example.com", avatar)
        assertNotNull(bitmap)
        assertTrue(bitmap!!.width <= 512)
        assertEquals(android.graphics.Color.RED, bitmap.getPixel(0, 0))
        bitmap.recycle()
    }

    @Test fun malformedImageLeavesAvatarEmpty() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertNull(loadAvatar(context, "https://example.com", "data:image/png;base64,bm90LWEtcGljdHVyZQ=="))
        assertNull(loadAvatar(context, "https://example.com", "file:///not-an-avatar"))
    }
}
