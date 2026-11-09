package com.example.ui.designsystem

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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

/** Metrics for the solid navy navigation bar / side rail (R1). */
object AuraBars {
    /** Solid bottom bar height. */
    val barHeight: Dp = 64.dp

    /** Solid wide rail width. */
    val railWidth: Dp = 72.dp

    /**
     * Bottom content padding for a screen whose content sits above the
     * docked bottom bar: bar height + the system bottom inset, so the
     * last item is never hidden behind the bar. In wide mode (side rail)
     * this is just harmless extra space at the list end.
     */
    @Composable
    fun contentBottomPadding(): Dp {
        val bottomInset = WindowInsets.safeDrawing.asPaddingValues().calculateBottomPadding()
        return barHeight + bottomInset
    }
}

/**
 * One navigation destination inside the solid bottom bar / side rail:
 * icon on a yellow pill indicator + label underneath, with a spring press
 * squash and a spring indicator morph. The badge (alerts count) sits on
 * the pill.
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
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = AuraMotion.spec { press },
        label = "navPress"
    )
    val indicatorScale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.6f,
        animationSpec = AuraMotion.spec { selection },
        label = "navIndicatorScale"
    )
    val indicatorAlpha by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = AuraMotion.spec { selection },
        label = "navIndicatorAlpha"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        // Centre the icon + label vertically in the bar. Without this the
        // Column defaults to top-aligned and the icons sit ~6dp high in
        // the 64dp bar, with extra empty space below the labels.
        verticalArrangement = Arrangement.Center,
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
                            color = RetroTokens.Yellow,
                            shape = AuraShapes.pill
                        )
                )
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (selected) {
                        RetroTokens.Background
                    } else {
                        RetroTokens.TextSecondary
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
                RetroTokens.Yellow
            } else {
                RetroTokens.TextSecondary
            }
        )
    }
}

/**
 * A circular solid button with a 48dp touch target and a spring press
 * squash: refresh, location, PiP, zoom, recenter. The Radar play button
 * passes a filled [containerColor] (primary) instead of the panel colour.
 * The icon itself comes from [content] so callers can show a progress
 * indicator instead (refresh while loading).
 */
@Composable
fun GlassIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.9f else 1f,
        animationSpec = AuraMotion.spec { press },
        label = "glassButtonPress"
    )
    RetroPanel(
        shape = CircleShape,
        color = containerColor,
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
 * A pill segmented control: one solid pill holding N options, the selected
 * option a filled pill that morphs in with the selection spring. Used for
 * the Air & Sky tab switcher and the Radar layer chooser. Every option cell
 * is the full 48dp height, so the touch target holds even though the pill
 * looks smaller.
 */
@Composable
fun PillSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    RetroPanel(
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
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
        animationSpec = AuraMotion.spec { press },
        label = "segmentPress"
    )
    val indicatorScale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.85f,
        animationSpec = AuraMotion.spec { selection },
        label = "segmentIndicator"
    )
    val indicatorAlpha by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = AuraMotion.spec { selection },
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
                    color = RetroTokens.Yellow,
                    shape = AuraShapes.pill
                )
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) {
                RetroTokens.Background
            } else {
                RetroTokens.TextSecondary
            },
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}
