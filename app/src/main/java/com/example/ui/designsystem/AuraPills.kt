package com.example.ui.designsystem

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.domain.model.AlertSeverity
import androidx.compose.material3.Icon

/**
 * Shared pill primitives for Phase 2.3. Every floating bar, pill control,
 * and status pill in the redesign is built from these — no screen hand-rolls
 * its own pill shape, glass, or press behaviour.
 */

/** Metrics for the floating navigation bar / rail. */
object AuraBars {
    /** Floating phone bar height. */
    val barHeight: Dp = 64.dp

    /** Side margins of the floating phone bar. */
    val sideMargin: Dp = 16.dp

    /** Gap between the floating phone bar and the bottom gesture area. */
    val bottomMargin: Dp = 12.dp

    /** Floating wide rail width. */
    val railWidth: Dp = 72.dp

    /**
     * Bottom content padding for a screen whose content scrolls under the
     * floating phone bar: bar height + bottom margin + the system bottom
     * inset, so the last item is never hidden behind the bar. In wide mode
     * (side rail) this is just harmless extra space at the list end.
     */
    @Composable
    fun contentBottomPadding(): Dp {
        val bottomInset = WindowInsets.safeDrawing.asPaddingValues().calculateBottomPadding()
        return barHeight + bottomMargin + bottomInset
    }
}

/**
 * The glass container for floating bars (and, from 2.5, glass cards).
 * Scheme-tinted scrim + hairline border + soft shadow. The real backdrop
 * blur lands in 2.4; until then this is the token-driven version of the
 * old fake glass — the tint comes from the scheme, not a hardcoded navy.
 */
@Composable
fun GlassSurface(
    level: GlassLevel,
    shape: Shape,
    modifier: Modifier = Modifier,
    shadowElevation: Dp = 8.dp,
    content: @Composable BoxScope.() -> Unit
) {
    Surface(
        modifier = modifier,
        shape = shape,
        color = AuraGlass.tint(MaterialTheme.colorScheme.surface, level),
        tonalElevation = 0.dp,
        shadowElevation = shadowElevation,
        border = BorderStroke(1.dp, Color.White.copy(alpha = AuraGlass.borderAlpha))
    ) {
        Box(content = content)
    }
}

/**
 * One navigation destination inside the floating bar / rail: icon on a
 * pill indicator + label underneath, with a spring press squash and a
 * spring indicator morph. The badge (alerts count) sits on the pill.
 *
 * Touch target: the caller gives this item its full cell (bar: weight +
 * full bar height; rail: full rail width), so the target is always ≥48dp
 * even though the pill itself looks smaller.
 */
@Composable
fun NavPillItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    badgeCount: Int?,
    isExpressive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val springs = AuraMotion.springs(isExpressive)
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = springs.press,
        label = "navPress"
    )
    val indicatorScale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.6f,
        animationSpec = springs.selection,
        label = "navIndicatorScale"
    )
    val indicatorAlpha by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = springs.selection,
        label = "navIndicatorAlpha"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.Tab,
                interactionSource = interactionSource,
                indication = null
            )
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
    ) {
        val indicator = @Composable {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(width = 52.dp, height = 32.dp)
            ) {
                // Pill indicator behind the icon; layout size stays fixed so
                // the icon never shifts when the indicator morphs in/out.
                Box(
                    modifier = Modifier
                        .size(width = 52.dp, height = 32.dp)
                        .graphicsLayer {
                            scaleX = indicatorScale
                            scaleY = indicatorScale
                            alpha = indicatorAlpha
                        }
                        .background(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = AuraShapes.pill
                        )
                )
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (selected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        Color.White.copy(alpha = 0.7f)
                    }
                )
            }
        }
        if (badgeCount != null) {
            BadgedBox(
                badge = {
                    Badge(containerColor = AuraColors.severityColor(AlertSeverity.Extreme)) {
                        Text("$badgeCount", style = MaterialTheme.typography.labelSmall)
                    }
                }
            ) {
                indicator()
            }
        } else {
            indicator()
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                Color.White.copy(alpha = 0.7f)
            }
        )
    }
}
