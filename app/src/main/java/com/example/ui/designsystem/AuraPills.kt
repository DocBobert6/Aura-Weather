package com.example.ui.designsystem

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.domain.model.AlertSeverity

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
 * A real backdrop blur of the recorded sky (2.4b) under a scheme-tinted
 * scrim, a 1dp hairline border with a brighter lit top edge, and a soft
 * ambient shadow (optionally tinted by the condition colour). When there
 * is no backdrop to blur — Radar (its backdrop is the map), low-RAM
 * devices, battery saver — the scrim is stronger and the blur is skipped,
 * legibility first. A filled [color] (e.g. the Radar play button) also
 * skips the blur: nothing shows through an opaque fill.
 */
@Composable
fun GlassSurface(
    level: GlassLevel,
    shape: Shape,
    modifier: Modifier = Modifier,
    shadowElevation: Dp = 8.dp,
    color: Color? = null,
    progressive: Boolean = false,
    progressiveEdge: ProgressiveEdge = ProgressiveEdge.Top,
    shadowColor: Color? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val blurState = LocalBackdropBlur.current
    val blurOn = blurState?.enabled == true && color == null
    val surface = MaterialTheme.colorScheme.surface
    val scrim = if (color != null) {
        color
    } else {
        val base = AuraGlass.tint(surface, level)
        if (blurOn) base else base.copy(alpha = (base.alpha + 0.12f).coerceAtMost(0.94f))
    }
    Surface(
        modifier = modifier.shadow(
            elevation = shadowElevation,
            shape = shape,
            ambientColor = shadowColor?.copy(alpha = 0.35f) ?: Color.Black,
            spotColor = shadowColor?.copy(alpha = 0.5f) ?: Color.Black
        ),
        shape = shape,
        color = Color.Transparent,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(1.dp, Color.White.copy(alpha = AuraGlass.borderAlpha))
    ) {
        Box {
            if (blurOn) {
                BackdropBlurCanvas(
                    level = level,
                    shape = shape,
                    progressive = progressive,
                    progressiveEdge = progressiveEdge
                )
            }
            // Scrim over the blurred (or plain) backdrop, then the lit edge.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(shape)
                    .background(scrim)
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(1.dp)
                    .clip(shape)
                    .background(Color.White.copy(alpha = 0.14f))
            )
            content()
        }
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
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = AuraMotion.spec(isExpressive) { press },
        label = "navPress"
    )
    val indicatorScale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.6f,
        animationSpec = AuraMotion.spec(isExpressive) { selection },
        label = "navIndicatorScale"
    )
    val indicatorAlpha by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = AuraMotion.spec(isExpressive) { selection },
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
                        // On the glass bar, not on the sky: the scheme's
                        // on-surface, so this stays legible in light mode too.
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
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
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            }
        )
    }
}

/**
 * A circular glass button with a 48dp touch target and a spring press
 * squash: refresh, location, PiP, zoom, recenter. The Radar play button
 * passes a filled [containerColor] (primary) instead of glass. The icon
 * itself comes from [content] so callers can show a progress indicator
 * instead (refresh while loading).
 */
@Composable
fun GlassIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isExpressive: Boolean = false,
    containerColor: Color? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.9f else 1f,
        animationSpec = AuraMotion.spec(isExpressive) { press },
        label = "glassButtonPress"
    )
    GlassSurface(
        level = GlassLevel.Bar,
        shape = CircleShape,
        color = containerColor,
        shadowElevation = 4.dp,
        modifier = modifier
            .size(48.dp)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clip(CircleShape)
            .clickable(
                enabled = enabled,
                onClick = onClick,
                role = Role.Button,
                interactionSource = interactionSource,
                indication = null
            )
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            content()
        }
    }
}

/**
 * A pill segmented control: one glass pill holding N options, the selected
 * option a filled pill that morphs in with the selection spring. Used for
 * the Air & Sky tab switcher, the Radar layer chooser, and (from 2.5) the
 * Settings palette selector. Every option cell is the full 48dp height, so
 * the touch target holds even though the pill looks smaller.
 */
@Composable
fun PillSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    isExpressive: Boolean,
    modifier: Modifier = Modifier
) {
    GlassSurface(
        level = GlassLevel.Bar,
        shape = AuraShapes.pill,
        modifier = modifier.height(48.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            options.forEachIndexed { index, label ->
                SegmentOption(
                    label = label,
                    selected = index == selectedIndex,
                    isExpressive = isExpressive,
                    onClick = { onSelect(index) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun SegmentOption(
    label: String,
    selected: Boolean,
    isExpressive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
        animationSpec = AuraMotion.spec(isExpressive) { press },
        label = "segmentPress"
    )
    val indicatorScale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.85f,
        animationSpec = AuraMotion.spec(isExpressive) { selection },
        label = "segmentIndicator"
    )
    val indicatorAlpha by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = AuraMotion.spec(isExpressive) { selection },
        label = "segmentIndicatorAlpha"
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxHeight()
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.Tab,
                interactionSource = interactionSource,
                indication = null
            )
    ) {
        // Filled pill behind the label; layout size stays fixed so the
        // label never shifts when the indicator morphs in/out.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 4.dp)
                .graphicsLayer {
                    scaleX = indicatorScale
                    scaleY = indicatorScale
                    alpha = indicatorAlpha
                }
                .background(
                    color = MaterialTheme.colorScheme.primary,
                    shape = AuraShapes.pill
                )
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
            },
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}
