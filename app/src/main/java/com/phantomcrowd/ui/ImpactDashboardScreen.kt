package com.phantomcrowd.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phantomcrowd.data.*
import com.phantomcrowd.ui.theme.DesignSystem
import java.text.SimpleDateFormat
import java.util.*

/**
 * Impact Dashboard Screen — shows real-time community impact statistics.
 *
 * All data is streamed live from Firestore via [MainViewModel.impactStats].
 * Collections used: issues, surface_anchors, authority_actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImpactDashboardScreen(viewModel: MainViewModel) {
    // Observe real-time stats from ViewModel
    val impactStats by viewModel.impactStats.collectAsState()

    // Pull-to-refresh state
    var isRefreshing by remember { mutableStateOf(false) }

    // Expansion state for category cards
    var expandedCategory by remember { mutableStateOf<String?>(null) }

    // Start/stop sync with lifecycle
    LaunchedEffect(Unit) {
        viewModel.startImpactDashboardSync()
    }
    DisposableEffect(Unit) {
        onDispose {
            viewModel.stopImpactDashboardSync()
        }
    }

    // Handle pull-to-refresh: briefly restart sync
    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            viewModel.stopImpactDashboardSync()
            kotlinx.coroutines.delay(300)
            viewModel.startImpactDashboardSync()
            kotlinx.coroutines.delay(700)
            isRefreshing = false
        }
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        if (impactStats == null) {
            // Loading state
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Loading impact data...")
                }
            }
        } else {
            val stats = impactStats!!

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ─── Header ─────────────────────────────────────────
                item {
                    Text(
                        "📊 Community Impact",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Real-time statistics from your community",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    // Last synced
                    val sdf = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
                    Text(
                        "Last synced: ${sdf.format(Date(stats.lastSyncedMs))}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }

                // ─── Overall Stats Card ─────────────────────────────
                item {
                    OverallStatsCard(stats)
                }

                // ─── Section header ─────────────────────────────────
                item {
                    Text(
                        "By Category",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                // ─── Category Breakdown Cards ───────────────────────
                if (stats.categoryBreakdowns.isEmpty()) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "No reports yet. Be the first to make your community safer!",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(20.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    items(
                        stats.categoryBreakdowns,
                        key = { it.category }
                    ) { breakdown ->
                        CategoryStatCard(
                            breakdown = breakdown,
                            isExpanded = expandedCategory == breakdown.category,
                            onToggleExpand = {
                                expandedCategory =
                                    if (expandedCategory == breakdown.category) null
                                    else breakdown.category
                            }
                        )
                    }
                }

                // ─── Your Contribution ──────────────────────────────
                item {
                    YourContributionCard(stats)
                }

                // ─── Success Stories ────────────────────────────────
                item {
                    SuccessStoriesSection(stats.successStories)
                }

                // Bottom spacer
                item {
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// Composable sub-components
// ═══════════════════════════════════════════════════════════════════

/**
 * Overall statistics card with 2×2 grid.
 */
@Composable
private fun OverallStatsCard(stats: ImpactStats) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = DesignSystem.Colors.primaryContainer
        ),
        shape = DesignSystem.Shapes.card
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem(
                    value = stats.totalReports.toString(),
                    label = "Issues Reported",
                    icon = "📋"
                )
                StatItem(
                    value = stats.issuesFixed.toString(),
                    label = "Issues Fixed",
                    icon = "✅"
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem(
                    value = stats.redZones.toString(),
                    label = "Red Zones",
                    icon = "🔴"
                )
                StatItem(
                    value = formatNumber(stats.estimatedReach),
                    label = "Estimated Reach",
                    icon = "👥"
                )
            }
        }
    }
}

/**
 * Single stat item.
 */
@Composable
private fun StatItem(value: String, label: String, icon: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(140.dp)
    ) {
        Text(icon, fontSize = 24.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            value,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Expandable card for a single category breakdown.
 */
@Composable
private fun CategoryStatCard(
    breakdown: CategoryBreakdown,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit
) {
    // Try to resolve a UseCase color, fall back to primary
    val useCaseEnum = UseCase.fromString(breakdown.category)
    val accentColor = useCaseEnum?.color ?: DesignSystem.Colors.primary

    Card(
        colors = CardDefaults.cardColors(
            containerColor = accentColor.copy(alpha = 0.1f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggleExpand() }
                .padding(16.dp)
        ) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(breakdown.icon, fontSize = 32.sp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            breakdown.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = accentColor
                        )
                        Text(
                            "${breakdown.total} reports",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                Icon(
                    if (isExpanded) Icons.Filled.KeyboardArrowUp
                    else Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Collapse" else "Expand"
                )
            }

            // Quick stats (always visible)
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                QuickStat("Fixed", breakdown.fixed.toString(), DesignSystem.Colors.success)
                QuickStat("Pending", breakdown.pending.toString(), DesignSystem.Colors.warning)
                QuickStat("In Progress", breakdown.inProgress.toString(), DesignSystem.Colors.primary)
                QuickStat(
                    "Rate",
                    "${(breakdown.resolutionRate * 100).toInt()}%",
                    DesignSystem.Colors.primary
                )
            }

            // Expanded content
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    Divider()
                    Spacer(modifier = Modifier.height(16.dp))

                    // Authority Actions (real data)
                    if (breakdown.recentActions.isNotEmpty()) {
                        Text(
                            "AUTHORITY ACTIONS",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        breakdown.recentActions.forEach { action ->
                            val actionIcon = when (action.actionType.uppercase()) {
                                "RESOLVED" -> "✅"
                                "IN_PROGRESS" -> "🔄"
                                "REJECTED" -> "❌"
                                else -> "📋"
                            }
                            val sdf = remember { SimpleDateFormat("MMM d", Locale.getDefault()) }
                            val dateStr = if (action.timestamp > 0)
                                sdf.format(Date(action.timestamp)) else ""
                            Text(
                                "$actionIcon ${action.actionType.replace("_", " ")} — ${action.notes.ifEmpty { action.adminEmail }} $dateStr",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    } else {
                        Text(
                            "⏳ Awaiting authority review",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }

                    // Top hotspots
                    if (breakdown.topHotspots.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "TOP HOTSPOTS",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        breakdown.topHotspots.forEachIndexed { index, hotspot ->
                            Text(
                                "${index + 1}. $hotspot",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Quick stat pill.
 */
@Composable
private fun QuickStat(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
    }
}

/**
 * Your contribution card.
 */
@Composable
private fun YourContributionCard(stats: ImpactStats) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🌟", fontSize = 28.sp)
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    "Your Contribution",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                if (stats.totalReports > 0)
                    "Keep reporting to make your community safer!"
                else
                    "Start reporting issues to make your community safer!",
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("⭐⭐⭐⭐⭐", fontSize = 16.sp)
                    Text(
                        "Community Trust",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

/**
 * Success stories section — real resolved issues from authority_actions.
 */
@Composable
private fun SuccessStoriesSection(stories: List<SuccessStory>) {
    Column {
        Text(
            "Success Stories",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (stories.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "📝 No resolved issues yet",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Reports are being reviewed by authorities. Check back soon!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            stories.forEach { story ->
                val sdf = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }
                val dateStr = if (story.resolvedAt > 0)
                    sdf.format(Date(story.resolvedAt)) else ""

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "✨ ${story.title}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            story.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (dateStr.isNotEmpty()) {
                            Text(
                                dateStr,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Format large numbers for display.
 */
private fun formatNumber(num: Int): String {
    return when {
        num >= 1000000 -> "${num / 1000000}M+"
        num >= 1000 -> "${num / 1000}K+"
        else -> num.toString()
    }
}
