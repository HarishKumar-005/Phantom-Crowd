package com.phantomcrowd.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun NearbyIssuesScreen(viewModel: MainViewModel) {
    val anchors by viewModel.anchors.collectAsState()
    val upvotedIds by viewModel.upvotedIssueIds.collectAsState()
    
    // Auto-refresh when entering
    LaunchedEffect(Unit) {
        viewModel.updateLocation()
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.updateLocation() }) {
                Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            if (anchors.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    Text("No issues nearby (50m)")
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(anchors) { anchor ->
                        IssueCard(
                            anchor = anchor,
                            isUpvoted = upvotedIds.contains(anchor.id),
                            onUpvote = { viewModel.upvoteIssue(anchor.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun IssueCard(
    anchor: com.phantomcrowd.data.AnchorData,
    isUpvoted: Boolean,
    onUpvote: () -> Unit
) {
    val date = Date(anchor.timestamp)
    val formattedDate = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(date)
    
    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(anchor.messageText, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(anchor.category, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                Text(formattedDate, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onUpvote,
                    enabled = !isUpvoted
                ) {
                    Icon(
                        imageVector = Icons.Filled.ThumbUp,
                        contentDescription = "Upvote",
                        tint = if (isUpvoted) MaterialTheme.colorScheme.primary else Color.Gray
                    )
                }
                Text(
                    text = "${anchor.upvotes}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isUpvoted) MaterialTheme.colorScheme.primary else Color.Gray
                )
            }
        }
    }
}
