package com.msa.freedoom.ui.fx

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.coroutines.delay

/** Soft, low-stiffness spring with a big overshoot: reads as jelly. */
private val jelly = spring<Float>(
    dampingRatio = Spring.DampingRatioHighBouncy,
    stiffness = Spring.StiffnessLow,
)

/** Squashes wide and flat while pressed, then wobbles back on release. */
fun Modifier.slimePress(source: InteractionSource): Modifier = composed {
    val pressed by source.collectIsPressedAsState()
    val sx by animateFloatAsState(
        targetValue = if (pressed) 1.08f else 1f,
        animationSpec = jelly,
        label = "slimeX",
    )
    val sy by animateFloatAsState(
        targetValue = if (pressed) 0.86f else 1f,
        animationSpec = jelly,
        label = "slimeY",
    )
    graphicsLayer {
        scaleX = sx
        scaleY = sy
    }
}

/** Pops in from small with a jelly overshoot; [delayMs] staggers a list. */
fun Modifier.slimeEntrance(key: Any, delayMs: Long = 0L): Modifier = composed {
    val s = remember(key) { Animatable(0.5f) }
    LaunchedEffect(key) {
        delay(delayMs)
        s.animateTo(1f, jelly)
    }
    graphicsLayer {
        scaleX = s.value
        scaleY = s.value
        alpha = (s.value * 2f - 0.6f).coerceIn(0f, 1f)
    }
}

/** Navigation icon that bounces up when its tab is selected. */
@Composable
fun SlimeNavIcon(icon: ImageVector, selected: Boolean) {
    val s by animateFloatAsState(
        targetValue = if (selected) 1.28f else 1f,
        animationSpec = jelly,
        label = "navSlime",
    )
    Icon(
        icon,
        contentDescription = null,
        modifier = Modifier.graphicsLayer {
            scaleX = s
            scaleY = s
        },
    )
}
