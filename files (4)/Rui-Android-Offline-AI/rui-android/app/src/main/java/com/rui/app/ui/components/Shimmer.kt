package com.rui.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp

@Composable
fun Modifier.shimmer(): Modifier {
val colors = MaterialTheme.colorScheme
val transition = rememberInfiniteTransition(label = "shimmer")

```
val animatedX = transition.animateFloat(
    initialValue = -400f,
    targetValue = 1200f,
    animationSpec = infiniteRepeatable(
        animation = tween(
            durationMillis = 1300,
            easing = LinearEasing
        ),
        repeatMode = RepeatMode.Restart
    ),
    label = "shimmerX"
)

val x = animatedX.value

return this.background(
    brush = Brush.linearGradient(
        colors = listOf(
            colors.surface,
            colors.primary.copy(alpha = 0.35f),
            colors.surface
        ),
        start = Offset(x, 0f),
        end = Offset(x + 400f, 0f)
    )
)
```

}

@Composable
fun ShimmerLines(modifier: Modifier = Modifier) {
Column(
modifier = modifier,
verticalArrangement = Arrangement.spacedBy(10.dp)
) {
listOf(1f, 0.86f, 0.55f).forEach { width ->
Box(
modifier = Modifier
.fillMaxWidth(width)
.height(14.dp)
.clip(RoundedCornerShape(7.dp))
.shimmer()
)
}
}
}
