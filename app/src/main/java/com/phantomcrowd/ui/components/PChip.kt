package com.phantomcrowd.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.phantomcrowd.ui.theme.DesignSystem

/**
 * Filter chip with selected/unselected states and 48dp minimum touch target.
 *
 * @param label      Display text.
 * @param selected   Whether the chip is currently active.
 * @param onClick    Tap handler.
 * @param modifier   Outer modifier.
 */
@Composable
fun PChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bg = if (selected) DesignSystem.Colors.primary else DesignSystem.Colors.surfaceVariant
    val fg = if (selected) DesignSystem.Colors.onPrimary else DesignSystem.Colors.onSurface

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .clip(DesignSystem.Shapes.chip)
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = DesignSystem.Spacing.md, vertical = DesignSystem.Spacing.xs)
            .semantics {
                contentDescription = if (selected) "$label, selected" else "$label, not selected"
                role = Role.Button
            }
    ) {
        Text(
            text = label,
            style = DesignSystem.Typography.bodyMedium,
            color = fg
        )
    }
}
