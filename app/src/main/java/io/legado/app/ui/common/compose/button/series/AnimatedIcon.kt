package io.legado.app.ui.common.compose.button.series

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

@Composable
fun AnimatedIcon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color? = null,
) {
    AnimatedContent(
        targetState = imageVector,
        transitionSpec = {
            (fadeIn() + scaleIn(initialScale = 0.8f))
                .togetherWith(fadeOut())
        },
        label = "IconTransition",
    ) { targetIcon ->
        Icon(
            imageVector = targetIcon,
            contentDescription = contentDescription,
            modifier = modifier,
            tint = tint ?: Color.Unspecified,
        )
    }
}
