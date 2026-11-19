package cc.samlab.classroom.ui.avatar

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF

// Adapted from CRTicket-Android cropSettingsAvatar. Keep JPEG bytes for multipart upload.
internal fun cropAvatar(source: Bitmap, zoom: Float, x: Float, y: Float): Bitmap {
    val size = 512
    val scale = maxOf(size.toFloat() / source.width, size.toFloat() / source.height) * zoom
    val width = source.width * scale
    val height = source.height * scale
    val left = (size - width) / 2 + x * (width - size) / 2
    val top = (size - height) / 2 + y * (height - size) / 2
    return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also { bitmap ->
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(source, null, RectF(left, top, left + width, top + height),
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
    }
}
