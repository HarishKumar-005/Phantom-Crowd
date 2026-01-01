package com.phantomcrowd.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phantomcrowd.data.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.*

/**
 * Shimmer effect for loading placeholders
 */
fun Modifier.shimmerEffect(): Modifier = composed {
    var size by remember { mutableStateOf(IntSize.Zero) }
    val transition = rememberInfiniteTransition(label = "Shimmer")
    val startOffsetX by transition.animateFloat(
        initialValue = -2 * size.width.toFloat(),
        targetValue = 2 * size.width.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1000)
        ),
        label = "ShimmerOffsetX"
    )

    background(
        brush = Brush.linearGradient(
            colors = listOf(
                Color(0xFFB8B5B5),
                Color(0xFF8F8B8B),
                Color(0xFFB8B5B5),
            ),
            start = Offset(startOffsetX, 0f),
            end = Offset(startOffsetX + size.width.toFloat(), size.height.toFloat())
        )
    )
    .onGloballyPositioned {
        size = it.size
    }
}

/**
 * Enhanced Nearby Issues Screen with filters and expandable cards.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NearbyIssuesScreen(
    viewModel: MainViewModel,
    onOpenARNavigation: ((AnchorData) -> Unit)? = null
) {
    val anchors by viewModel.anchors.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val currentLocation by viewModel.currentLocation.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // Filter state
    var selectedUseCase by remember { mutableStateOf<UseCase?>(null) }
    var selectedSort by remember { mutableStateOf(SortOption.RECENT) }
    var expandedCardId by remember { mutableStateOf<String?>(null) }
    
    // Dropdown menu states
    var showUseCaseDropdown by remember { mutableStateOf(false) }
    var showSortDropdown by remember { mutableStateOf(false) }
    
    // Filter and sort the anchors
    val filteredAnchors = remember(anchors, selectedUseCase, selectedSort, currentLocation) {
        var result = anchors.toList()
        
        // Filter by use case
        if (selectedUseCase != null) {
            result = result.filter { it.useCase == selectedUseCase!!.name }
        }
        
        // Sort
        result = when (selectedSort) {
            SortOption.RECENT -> result.sortedByDescending { it.timestamp }
            SortOption.POPULAR -> result.sortedByDescending { it.upvotes }
            SortOption.URGENT -> result.sortedWith(
                compareBy<AnchorData> { 
                    Severity.fromString(it.severity).priority 
                }.thenByDescending { it.timestamp }
            )
            SortOption.NEAREST -> {
                if (currentLocation != null) {
                    result.sortedBy { anchor ->
                        calculateDistance(
                            currentLocation!!.latitude, currentLocation!!.longitude,
                            anchor.latitude, anchor.longitude
                        )
                    }
                } else {
                    result.sortedByDescending { it.timestamp }
                }
            }
        }
        
        result
    }
    
    // Auto-refresh when entering
    LaunchedEffect(Unit) {
        viewModel.updateLocation()
    }

    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    viewModel.updateLocation()
                }
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            // Filter Row
            FilterRow(
                selectedUseCase = selectedUseCase,
                selectedSort = selectedSort,
                showUseCaseDropdown = showUseCaseDropdown,
                showSortDropdown = showSortDropdown,
                onUseCaseDropdownToggle = { showUseCaseDropdown = it },
                onSortDropdownToggle = { showSortDropdown = it },
                onUseCaseSelected = { 
                    selectedUseCase = it
                    showUseCaseDropdown = false
                },
                onSortSelected = {
                    selectedSort = it
                    showSortDropdown = false
                }
            )
            
            if (isLoading) {
                // Loading skeleton
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(5) {
                        LoadingCardSkeleton()
                    }
                }
            } else if (filteredAnchors.isEmpty()) {
                // Empty state
                EmptyState(
                    hasFilters = selectedUseCase != null,
                    onClearFilters = { selectedUseCase = null }
                )
            } else {
                // Issue list
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredAnchors, key = { it.id }) { anchor ->
                        EnhancedIssueCard(
                            anchor = anchor,
                            isExpanded = expandedCardId == anchor.id,
                            currentLocation = currentLocation,
                            viewModel = viewModel,
                            onToggleExpand = {
                                expandedCardId = if (expandedCardId == anchor.id) null else anchor.id
                            },
                            onNavigate = onOpenARNavigation?.let { callback ->
                                {
                                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                    viewModel.setSelectedAnchor(anchor)
                                    callback(anchor)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Filter row with use case and sort dropdowns
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterRow(
    selectedUseCase: UseCase?,
    selectedSort: SortOption,
    showUseCaseDropdown: Boolean,
    showSortDropdown: Boolean,
    onUseCaseDropdownToggle: (Boolean) -> Unit,
    onSortDropdownToggle: (Boolean) -> Unit,
    onUseCaseSelected: (UseCase?) -> Unit,
    onSortSelected: (SortOption) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Use Case Filter
        Box {
            FilterChip(
                selected = selectedUseCase != null,
                onClick = { onUseCaseDropdownToggle(true) },
                label = {
                    Text(
                        if (selectedUseCase != null) {
                            "${selectedUseCase.icon} ${selectedUseCase.label}"
                        } else {
                            "All Categories ▼"
                        }
                    )
                },
                leadingIcon = if (selectedUseCase != null) {
                    {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                } else null
            )
            
            DropdownMenu(
                expanded = showUseCaseDropdown,
                onDismissRequest = { onUseCaseDropdownToggle(false) }
            ) {
                DropdownMenuItem(
                    text = { Text("All Categories") },
                    onClick = { onUseCaseSelected(null) },
                    leadingIcon = if (selectedUseCase == null) {
                        { Icon(Icons.Filled.Check, contentDescription = null) }
                    } else null
                )
                Divider()
                UseCase.entries.forEach { useCase ->
                    DropdownMenuItem(
                        text = { Text("${useCase.icon} ${useCase.label}") },
                        onClick = { onUseCaseSelected(useCase) },
                        leadingIcon = if (selectedUseCase == useCase) {
                            { Icon(Icons.Filled.Check, contentDescription = null) }
                        } else null
                    )
                }
            }
        }
        
        // Sort Filter
        Box {
            FilterChip(
                selected = true,
                onClick = { onSortDropdownToggle(true) },
                label = {
                    Text("${selectedSort.icon} ${selectedSort.label} ▼")
                }
            )
            
            DropdownMenu(
                expanded = showSortDropdown,
                onDismissRequest = { onSortDropdownToggle(false) }
            ) {
                SortOption.entries.forEach { sortOption ->
                    DropdownMenuItem(
                        text = { Text("${sortOption.icon} ${sortOption.label}") },
                        onClick = { onSortSelected(sortOption) },
                        leadingIcon = if (selectedSort == sortOption) {
                            { Icon(Icons.Filled.Check, contentDescription = null) }
                        } else null
                    )
                }
            }
        }
    }
}

/**
 * Loading skeleton for cards
 */
@Composable
private fun LoadingCardSkeleton() {
    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .width(120.dp)
                        .height(20.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .shimmerEffect()
                )
                Box(
                    modifier = Modifier
                        .width(80.dp)
                        .height(20.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .shimmerEffect()
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(20.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .shimmerEffect()
            )
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .height(16.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .shimmerEffect()
            )
            Spacer(modifier = Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.4f)
                    .height(16.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .shimmerEffect()
            )
        }
    }
}

/**
 * Empty state when no issues match filters
 */
@Composable
private fun EmptyState(
    hasFilters: Boolean,
    onClearFilters: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Text("✨", fontSize = 64.sp)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "All Clear!",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                if (hasFilters) {
                    "No issues match your current filters."
                } else {
                    "No issues reported in this area.\nEverything looks good!"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            if (hasFilters) {
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(onClick = onClearFilters) {
                    Text("Clear Filters")
                }
            }
        }
    }
}

/**
 * Enhanced issue card with expandable details
 */
@Composable
fun EnhancedIssueCard(
    anchor: AnchorData,
    isExpanded: Boolean,
    currentLocation: android.location.Location?,
    viewModel: MainViewModel? = null,
    onToggleExpand: () -> Unit,
    onNavigate: (() -> Unit)? = null
) {
    val date = Date(anchor.timestamp)
    val formattedDate = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(date)
    val timeAgo = getTimeAgo(anchor.timestamp)
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // Parse use case and severity
    val useCase = UseCase.fromString(anchor.useCase)
    val severity = Severity.fromString(anchor.severity)
    
    // Calculate distance if location available
    val distance = currentLocation?.let {
        calculateDistance(it.latitude, it.longitude, anchor.latitude, anchor.longitude)
    }
    
    // Track if user already upvoted this issue
    val prefs = context.getSharedPreferences("upvotes", android.content.Context.MODE_PRIVATE)
    var hasUpvoted by remember { mutableStateOf(prefs.getBoolean(anchor.id, false)) }
    var localUpvotes by remember { mutableIntStateOf(anchor.upvotes) }
    
    // Fade in animation
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        alpha.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 500)
        )
    }
    
    // Card background color based on use case
    val cardBackgroundColor = useCase?.color?.copy(alpha = 0.05f) 
        ?: MaterialTheme.colorScheme.surface

    Card(
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isExpanded) 8.dp else 2.dp
        ),
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer(alpha = alpha.value)
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            ),
        colors = CardDefaults.cardColors(containerColor = cardBackgroundColor)
    ) {
        Column(modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggleExpand() }
            .padding(16.dp)
        ) {
            // Header Row: Use Case + Severity
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Use case badge
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (useCase != null) {
                        Text(useCase.icon, fontSize = 20.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            useCase.label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = useCase.color
                        )
                    } else {
                        Text(
                            anchor.category.replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                
                // Severity badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(severity.color.copy(alpha = 0.2f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        "${severity.icon} ${severity.label.uppercase()}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = severity.color
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Description
            Text(
                anchor.messageText,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = if (isExpanded) Int.MAX_VALUE else 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Metadata row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (anchor.locationName.isNotEmpty()) {
                    Text(
                        "📍 ${anchor.locationName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    "⏰ $timeAgo",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // Distance if available
            if (distance != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "📏 ${formatDistance(distance)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Impact metric
            if (anchor.nearbyIssueCount > 0 || useCase != null) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("👥", fontSize = 16.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            if (useCase != null && anchor.nearbyIssueCount > 0) {
                                "${anchor.nearbyIssueCount} people reported issues in this area"
                            } else {
                                "Help us gather more data about this location"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
            
            // Engagement row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Upvote button
                FilledTonalButton(
                    onClick = {
                        if (!hasUpvoted) {
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            viewModel?.upvoteIssue(anchor.id)
                            hasUpvoted = true
                            localUpvotes++
                            prefs.edit().putBoolean(anchor.id, true).apply()
                        }
                    },
                    enabled = !hasUpvoted,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = if (hasUpvoted) 
                            MaterialTheme.colorScheme.primaryContainer 
                        else 
                            MaterialTheme.colorScheme.secondaryContainer
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(
                        "👍 $localUpvotes",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                
                // Expand indicator
                Text(
                    if (isExpanded) "▲ Less" else "▼ More",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
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
                    
                    // Full details
                    Text(
                        "Details",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Location details
                    Row {
                        Text("📍 Coordinates: ", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                        Text(
                            "${String.format("%.5f", anchor.latitude)}, ${String.format("%.5f", anchor.longitude)}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row {
                        Text("🕐 Reported: ", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                        Text(formattedDate, style = MaterialTheme.typography.bodySmall)
                    }
                    
                    if (anchor.useCaseCategory.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row {
                            Text("📋 Category: ", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                            Text(
                                anchor.useCaseCategory.replace("_", " ").lowercase()
                                    .replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Action buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Navigate button
                        if (onNavigate != null) {
                            OutlinedButton(
                                onClick = { onNavigate() },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("🧭 Navigate")
                            }
                        }
                        
                        // Share button
                        OutlinedButton(
                            onClick = {
                                val shareText = buildString {
                                    append("🚨 ${useCase?.icon ?: "📍"} Issue Alert\n\n")
                                    append("${anchor.messageText}\n\n")
                                    append("📍 Location: ${anchor.latitude}, ${anchor.longitude}\n")
                                    append("Report via Phantom Crowd")
                                }
                                val sendIntent = android.content.Intent().apply {
                                    action = android.content.Intent.ACTION_SEND
                                    putExtra(android.content.Intent.EXTRA_TEXT, shareText)
                                    type = "text/plain"
                                }
                                context.startActivity(android.content.Intent.createChooser(sendIntent, "Share"))
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("🔗 Share")
                        }
                    }
                }
            }
        }
    }
}

/**
 * Calculate distance between two coordinates (Haversine formula)
 */
private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val earthRadius = 6371000.0 // meters
    
    val lat1Rad = Math.toRadians(lat1)
    val lat2Rad = Math.toRadians(lat2)
    val deltaLat = Math.toRadians(lat2 - lat1)
    val deltaLon = Math.toRadians(lon2 - lon1)
    
    val a = sin(deltaLat / 2) * sin(deltaLat / 2) +
            cos(lat1Rad) * cos(lat2Rad) *
            sin(deltaLon / 2) * sin(deltaLon / 2)
    
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    
    return earthRadius * c
}

/**
 * Format distance for display
 */
private fun formatDistance(meters: Double): String {
    return when {
        meters < 100 -> "${meters.toInt()}m away"
        meters < 1000 -> "${(meters / 10).toInt() * 10}m away"
        else -> "${String.format("%.1f", meters / 1000)}km away"
    }
}

/**
 * Get human-readable time ago string
 */
private fun getTimeAgo(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    
    val seconds = diff / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24
    
    return when {
        seconds < 60 -> "Just now"
        minutes < 60 -> "${minutes}m ago"
        hours < 24 -> "${hours}h ago"
        days < 7 -> "${days}d ago"
        else -> SimpleDateFormat("MMM dd", Locale.getDefault()).format(Date(timestamp))
    }
}
