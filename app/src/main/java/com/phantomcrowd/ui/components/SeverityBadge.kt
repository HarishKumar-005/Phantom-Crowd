package com.phantomcrowd.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.phantomcrowd.ui.theme.DesignSystem

/**
 * Small pill badge showing issue severity with accessible label.
 *
 * @param severityName  One of "URGENT", "HIGH", "MEDIUM", "LOW".
 * @param modifier      Outer modifier.
 */
@Composable
fun SeverityBadge(
    severityName: String,
    modifier: Modifier = Modifier
) {
    val (bg, label) = when (severityName.uppercase()) {
        "URGENT" -> DesignSystem.Colors.error to "Urgent"
        "HIGH"   -> DesignSystem.Colors.severityHigh to "High"
        "MEDIUM" -> DesignSystem.Colors.severityMedium to "Medium"
        "LOW"    -> DesignSystem.Colors.severityLow to "Low"
        else     -> DesignSystem.Colors.neutralMuted to severityName
    }

    Box(
        modifier = modifier
            .clip(DesignSystem.Shapes.pill)
            .background(bg)
            .padding(horizontal = DesignSystem.Spacing.xs, vertical = DesignSystem.Spacing.xxs)
            .semantics { contentDescription = "Severity: $label" }
    ) {
        Text(
            text = label,
            style = DesignSystem.Typography.labelLarge,
            color = Color.White
        )
    }
}
