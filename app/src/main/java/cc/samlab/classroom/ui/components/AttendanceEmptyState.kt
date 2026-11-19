package cc.samlab.classroom.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.toPath
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner

// Uses the same centered, expressive empty-state layout as CRTicket-Android.
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AttendanceEmptyState(message: String, icon: Int) {
    val lifecycle = (LocalContext.current as? LifecycleOwner)?.lifecycle
    var resumed by remember(lifecycle) { mutableStateOf(lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) == true) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ -> resumed = lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) == true }
        lifecycle?.addObserver(observer)
        onDispose { lifecycle?.removeObserver(observer) }
    }
    val phase = if (resumed) rememberInfiniteTransition(label = "empty-state").animateFloat(
        0f, 1f, infiniteRepeatable(tween(3200, easing = LinearEasing), RepeatMode.Reverse), label = "shape",
    ) else remember { mutableFloatStateOf(0f) }
    val morph = remember { Morph(MaterialShapes.Cookie9Sided, MaterialShapes.Circle) }
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(Modifier.size(128.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val amount = phase.value
                scale(size.width, size.height, pivot = Offset.Zero) {
                    rotate(amount * 24f, pivot = Offset(0.5f, 0.5f)) {
                        drawPath(morph.toPath(amount).asComposePath(), colors.secondaryContainer)
                    }
                }
            }
            Icon(painterResource(icon), null, Modifier.size(52.dp), tint = colors.primary)
        }
        Text(message, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}
