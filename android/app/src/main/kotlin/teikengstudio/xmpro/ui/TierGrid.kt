package teikengstudio.xmpro.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** One fixed-size slice of an episode list. */
data class Tier(val from: Int, val to: Int) {
    val label: String get() = "$from-$to"
}

/**
 * Splits [total] episodes into equal slices: 100 per block, three per row.
 */
fun tiersOf(total: Int, step: Int = TIER_STEP): List<Tier> {
    if (total <= 0) return emptyList()
    val result = ArrayList<Tier>(total / step + 1)
    var from = 1
    while (from <= total) {
        val to = minOf(from + step - 1, total)
        result += Tier(from, to)
        from = to + 1
    }
    return result
}

const val TIER_STEP = 100

/**
 * Grid of episode ranges.
 *
 * Three columns per row with the active slice highlighted; a lone last item is
 * padded so the column widths never shift.
 */
@Composable
fun TierGrid(
    total: Int,
    selectedStart: Int?,
    selectedEnd: Int?,
    modifier: Modifier = Modifier,
    step: Int = TIER_STEP,
    enabled: Boolean = true,
    onPick: (Tier) -> Unit,
) {
    val tiers = tiersOf(total, step)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (row in tiers.chunked(3)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (tier in row) {
                    val active = selectedStart == tier.from && selectedEnd == tier.to
                    Surface(
                        onClick = { onPick(tier) },
                        enabled = enabled,
                        shape = RoundedCornerShape(14.dp),
                        color = if (active) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            tier.label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (active) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 11.dp),
                        )
                    }
                }
                // Keep the grid aligned when the last row is not full.
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** Header used above a grid: total count plus the active slice. */
@Composable
fun TierHeader(total: Int, selectedStart: Int?, selectedEnd: Int?, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "共 $total 集",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        if (selectedStart != null && selectedEnd != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                "只看 $selectedStart-$selectedEnd",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
