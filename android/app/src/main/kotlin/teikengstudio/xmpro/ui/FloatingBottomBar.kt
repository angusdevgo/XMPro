package teikengstudio.xmpro.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class FloatingNavItem(
    val title: String,
    val selectedIcon: ImageVector,
    val icon: ImageVector,
)

/**
 * Floating pill navigation bar with a spring-driven slider.
 *
 * The indicator moves with `graphicsLayer.translationX` only, so switching tabs
 * never re-measures the bar; positions are recorded in px by layout callbacks.
 */
@Composable
fun FloatingBottomBar(
    items: List<FloatingNavItem>,
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    /** Capsule backplate; pass null for a fully transparent bar. */
    containerColor: Color? = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f),
    indicatorColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.20f),
) {
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val positions = remember {
        mutableStateListOf<Float>().apply { repeat(items.size) { add(0f) } }
    }
    var tabWidthPx by remember { mutableFloatStateOf(0f) }
    val indicatorX by animateFloatAsState(
        targetValue = positions.getOrNull(selectedIndex) ?: 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "bottomBarIndicator",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .height(64.dp)
                .padding(horizontal = 6.dp)
                .then(
                    if (containerColor == null) {
                        Modifier
                    } else {
                        Modifier
                            .shadow(elevation = 8.dp, shape = CircleShape, clip = false)
                            .clip(CircleShape)
                            .background(containerColor)
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                                shape = CircleShape,
                            )
                    },
                ),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (tabWidthPx > 0f) {
                Box(
                    modifier = Modifier
                        .graphicsLayer { translationX = indicatorX }
                        .size(
                            width = with(density) { (tabWidthPx * 0.82f).toDp() },
                            // Taller than the icon + label stack so the pill fully
                            // surrounds the tab content instead of clipping it.
                            height = 52.dp,
                        )
                        .clip(CircleShape)
                        .background(indicatorColor),
                )
            }
            Row(
                modifier = Modifier.height(64.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items.forEachIndexed { index, item ->
                    val selected = index == selectedIndex
                    Box(
                        modifier = Modifier
                            .width(84.dp)
                            .height(64.dp)
                            .onGloballyPositioned { coordinates ->
                                val width = coordinates.size.width.toFloat()
                                val indicatorWidth = width * 0.82f
                                val start = coordinates.positionInParent().x + (width - indicatorWidth) / 2f
                                if (tabWidthPx != width) tabWidthPx = width
                                if (index < positions.size && positions[index] != start) positions[index] = start
                            }
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) {
                                if (index != selectedIndex) {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onItemSelected(index)
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        androidx.compose.foundation.layout.Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Icon(
                                imageVector = if (selected) item.selectedIcon else item.icon,
                                contentDescription = item.title,
                                tint = if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.size(22.dp),
                            )
                            Text(
                                text = item.title,
                                fontSize = 11.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
