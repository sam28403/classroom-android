package cc.samlab.classroom

import android.graphics.Bitmap
import android.graphics.Color
import cc.samlab.classroom.ui.avatar.cropAvatar
import org.junit.Assert.assertEquals
import org.junit.Test

class AvatarCropDeviceTest {
    @Test fun cropPositionMatchesPreviewAndProducesSquare() {
        val source = Bitmap.createBitmap(800, 400, Bitmap.Config.ARGB_8888)
        for (x in 0 until source.width) {
            for (y in 0 until source.height) {
                source.setPixel(x, y, if (x < 400) Color.RED else Color.BLUE)
            }
        }
        // Positive horizontal offset moves the image right, exposing its left side.
        val left = cropAvatar(source, 1f, 1f, 0f)
        val right = cropAvatar(source, 1f, -1f, 0f)
        assertEquals(512, left.width)
        assertEquals(512, left.height)
        assertEquals(Color.RED, left.getPixel(256, 256))
        assertEquals(Color.BLUE, right.getPixel(256, 256))
        left.recycle()
        right.recycle()
        source.recycle()
    }
}
