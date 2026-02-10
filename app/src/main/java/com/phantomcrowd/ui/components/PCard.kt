package com.phantomcrowd.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.phantomcrowd.ui.theme.DesignSystem

/**
 * Parameterized card built on design-system tokens.
 *
 * @param description Accessibility label read by TalkBack.
 * @param onClick     Optional click handler; card is non-clickable when null.
 * @param modifier    Outer modifier forwarded to the underlying [Card].
 * @param content     Composable slot rendered inside the card.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PCard(
    description: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val cardModifier = modifier
        .fillMaxWidth()
        .semantics { contentDescription = description }

    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = cardModifier,
            shape = DesignSystem.Shapes.card,
            colors = CardDefaults.cardColors(
                containerColor = DesignSystem.Colors.surface
            ),
            elevation = CardDefaults.cardElevation(
                defaultElevation = DesignSystem.Elevation.smallCard
            ),
            border = BorderStroke(1.dp, DesignSystem.Colors.outline)
        ) {
            Column(modifier = Modifier.padding(DesignSystem.Spacing.md)) {
                content()
            }
        }
    } else {
        Card(
            modifier = cardModifier,
            shape = DesignSystem.Shapes.card,
            colors = CardDefaults.cardColors(
                containerColor = DesignSystem.Colors.surface
            ),
            elevation = CardDefaults.cardElevation(
                defaultElevation = DesignSystem.Elevation.smallCard
            ),
            border = BorderStroke(1.dp, DesignSystem.Colors.outline)
        ) {
            Column(modifier = Modifier.padding(DesignSystem.Spacing.md)) {
                content()
            }
        }
    }
}
