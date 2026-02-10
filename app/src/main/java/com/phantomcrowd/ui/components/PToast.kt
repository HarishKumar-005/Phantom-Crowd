package com.phantomcrowd.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import com.phantomcrowd.ui.theme.DesignSystem

/**
 * Accessible snackbar-style toast using design-system tokens.
 *
 * @param snackbarData  Snackbar data from SnackbarHostState.
 * @param modifier      Outer modifier.
 */
@Composable
fun PToast(
    snackbarData: SnackbarData,
    modifier: Modifier = Modifier
) {
    Snackbar(
        snackbarData = snackbarData,
        containerColor = DesignSystem.Colors.onSurface,
        contentColor = DesignSystem.Colors.inverseOnSurface,
        actionColor = DesignSystem.Colors.primaryContainer,
        shape = DesignSystem.Shapes.card,
        modifier = modifier
            .padding(DesignSystem.Spacing.md)
            .semantics {
                contentDescription = snackbarData.visuals.message
                liveRegion = LiveRegionMode.Polite
            }
    )
}
