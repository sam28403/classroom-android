package cc.samlab.classroom.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import cc.samlab.classroom.data.loadAvatar

@Composable
fun UserAvatar(server: String, avatar: String?, userKey: String?, size: Dp = 50.dp) {
    val context = LocalContext.current.applicationContext
    // Reset immediately on account/server changes; never display the previous user's image.
    var bitmap by remember(server, avatar, userKey) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(server, avatar, userKey) {
        if (userKey != null && !avatar.isNullOrBlank()) {
            bitmap = loadAvatar(context, server, avatar)?.asImageBitmap()
        }
    }
    Box(Modifier.size(size).clip(CircleShape)) {
        bitmap?.let {
            Image(it, contentDescription = null, modifier = Modifier.size(size),
                contentScale = ContentScale.Crop)
        }
    }
}
