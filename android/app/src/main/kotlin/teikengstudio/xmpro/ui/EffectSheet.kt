package teikengstudio.xmpro.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import teikengstudio.xmpro.player.EffectPreset

/**
 * Sound preset picker for the player sheet.
 *
 * Presets apply immediately: the sheet is the place where the effect is judged
 * by ear, so nothing here needs a confirm step.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EffectSheet(
    preset: String,
    strength: Int,
    onPreset: (String) -> Unit,
    onStrength: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val current = EffectPreset.of(preset)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "音效",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    current.label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            FlowRow(
                Modifier.fillMaxWidth().padding(top = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (option in EffectPreset.entries) {
                    val active = option == current
                    Surface(
                        onClick = { onPreset(option.id) },
                        shape = RoundedCornerShape(50),
                        color = if (active) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                    ) {
                        Text(
                            option.label,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (active) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                        )
                    }
                }
            }
            if (current != EffectPreset.Off) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("强度", style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = strength / 100f,
                        onValueChange = { onStrength((it * 100).roundToInt()) },
                        steps = 0,
                        modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                    )
                    Text(
                        "$strength%",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                "由系统音频效果实现，部分机型不支持某一档时会自动跳过。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp, bottom = 18.dp),
            )
        }
    }
}
