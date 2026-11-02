package com.iqoo.launcher

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.using
import androidx.compose.ui.unit.IntSize

/** Shared, interruptible spring specs for the launcher’s small set of core transitions. */
object VelocityMotion {
    val container = spring<IntSize>(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy)
    val gesture = spring<Float>(stiffness = Spring.StiffnessLow, dampingRatio = 0.88f)
    val emphasis = spring<Float>(stiffness = Spring.StiffnessMedium, dampingRatio = 0.9f)

    fun screenTransition(opening: Boolean): ContentTransform =
        (if (opening) fadeIn() + scaleIn(initialScale = 0.98f)
         else fadeIn(initialAlpha = 0.96f) + scaleIn(initialScale = 1.01f))
            .togetherWith(if (opening) fadeOut() + scaleOut(targetScale = 1.01f)
                          else fadeOut() + scaleOut(targetScale = 0.98f))
            .using(androidx.compose.animation.SizeTransform(clip = false))
}
