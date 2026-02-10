package com.phantomcrowd.ui.components

import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.phantomcrowd.ui.theme.DesignSystem

/**
 * Consistent top app bar using design-system tokens.
 *
 * @param title     Screen title.
 * @param modifier  Outer modifier.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PTopBar(
    title: String,
    modifier: Modifier = Modifier
) {
    CenterAlignedTopAppBar(
        title = {
            Text(
                text = title,
                style = DesignSystem.Typography.titleLarge,
                color = DesignSystem.Colors.onSurface,
                modifier = Modifier.semantics { heading() }
            )
        },
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = DesignSystem.Colors.surface,
            titleContentColor = DesignSystem.Colors.onSurface
        ),
        modifier = modifier
    )
}
