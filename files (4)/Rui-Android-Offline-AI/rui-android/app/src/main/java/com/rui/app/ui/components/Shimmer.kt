import androidx.compose.runtime.getValue
package com.rui.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Arrangement

/** Animated sweep used while the model is "thinking" and no token has arrived yet. */
@Composable
fun Modifier.shimmer(): Modifier {
    val cs = MaterialTheme.colorScheme
    val t = rememberInfiniteTransition(label = "shimmer")
    val x by t.animateFloat(
        initialValue = -400f, targetValue = 1200f,
        animationSpec = infiniteRepeatable(tween(1300, easing = LinearEasing), RepeatMode.Restart),
        label = "x",
    )
    return background(
        Brush.linearGradient(
            colors = listOf(cs.surfaceContainer, cs.primary.copy(alpha = 0.35f), cs.surfaceContainer),
            start = androidx.compose.ui.geometry.Offset(x, 0f),
            end = androidx.compose.ui.geometry.Offset(x + 400f, 0f),
        )
    )
}

@Composable
fun ShimmerLines(modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(1f, 0.86f, 0.55f).forEach { w ->
            Box(Modifier.fillMaxWidth(w).height(14.dp).clip(RoundedCornerShape(7.dp)).shimmer())
        }
    }
}
