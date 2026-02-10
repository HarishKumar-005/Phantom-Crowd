package com.phantomcrowd.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.phantomcrowd.ui.theme.DesignSystem

/**
 * Tab descriptor for the bottom navigation.
 */
data class PNavTab(
    val index: Int,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

/**
 * Bottom navigation bar with a center FAB for the Post action.
 *
 * @param selectedTab   Currently active tab index.
 * @param onTabSelected Called when user taps a tab.
 * @param onPostClick   Called when user taps the center FAB.
 */
@Composable
fun PBottomNav(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    onPostClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tabs = listOf(
        PNavTab(0, "Nearby",   Icons.Filled.Home,       Icons.Outlined.Home),
        PNavTab(2, "Map",      Icons.Filled.LocationOn, Icons.Outlined.LocationOn),
        // Index 1 is Post (FAB) — handled separately
        PNavTab(3, "Navigate", Icons.Filled.PlayArrow,  Icons.Outlined.PlayArrow),
        PNavTab(5, "Impact",   Icons.Filled.Info,        Icons.Outlined.Info)
    )

    NavigationBar(
        containerColor = DesignSystem.Colors.surface,
        tonalElevation = DesignSystem.Elevation.smallCard,
        modifier = modifier
    ) {
        // Left tabs (Nearby, Map)
        tabs.take(2).forEach { tab ->
            NavigationBarItem(
                selected = selectedTab == tab.index,
                onClick = { onTabSelected(tab.index) },
                icon = {
                    Icon(
                        if (selectedTab == tab.index) tab.selectedIcon else tab.unselectedIcon,
                        contentDescription = tab.label
                    )
                },
                label = { Text(tab.label, style = DesignSystem.Typography.labelLarge) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = DesignSystem.Colors.primary,
                    selectedTextColor = DesignSystem.Colors.primary,
                    unselectedIconColor = DesignSystem.Colors.neutralMuted,
                    unselectedTextColor = DesignSystem.Colors.neutralMuted,
                    indicatorColor = DesignSystem.Colors.primaryContainer
                )
            )
        }

        // Center FAB
        NavigationBarItem(
            selected = selectedTab == 1,
            onClick = { onPostClick() },
            icon = {
                FloatingActionButton(
                    onClick = onPostClick,
                    containerColor = DesignSystem.Colors.primary,
                    contentColor = DesignSystem.Colors.onPrimary,
                    elevation = FloatingActionButtonDefaults.elevation(
                        defaultElevation = DesignSystem.Elevation.card
                    ),
                    modifier = Modifier
                        .size(48.dp)
                        .semantics { contentDescription = "Report an issue" }
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Post")
                }
            },
            label = { Text("Post", style = DesignSystem.Typography.labelLarge) },
            colors = NavigationBarItemDefaults.colors(
                selectedTextColor = DesignSystem.Colors.primary,
                unselectedTextColor = DesignSystem.Colors.neutralMuted,
                indicatorColor = DesignSystem.Colors.surface // hide indicator behind FAB
            )
        )

        // Right tabs (Navigate, Impact)
        tabs.drop(2).forEach { tab ->
            NavigationBarItem(
                selected = selectedTab == tab.index,
                onClick = { onTabSelected(tab.index) },
                icon = {
                    Icon(
                        if (selectedTab == tab.index) tab.selectedIcon else tab.unselectedIcon,
                        contentDescription = tab.label
                    )
                },
                label = { Text(tab.label, style = DesignSystem.Typography.labelLarge) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = DesignSystem.Colors.primary,
                    selectedTextColor = DesignSystem.Colors.primary,
                    unselectedIconColor = DesignSystem.Colors.neutralMuted,
                    unselectedTextColor = DesignSystem.Colors.neutralMuted,
                    indicatorColor = DesignSystem.Colors.primaryContainer
                )
            )
        }
    }
}
