package com.phantomcrowd.ui

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.phantomcrowd.data.AnchorData
import com.phantomcrowd.utils.Logger
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Simplified Posting Screen - Text-only issue reporting.
 * Features:
 * - Form-based input for message and category
 * - Location-based posting
 * - Firestore upload (no Firebase Storage required)
 * - AR Surface Placement option (Phase I)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostCreationARScreen(
    viewModel: MainViewModel,
    onPostCreated: () -> Unit,
    onCancel: () -> Unit,
    onOpenARPlacement: ((messageText: String, category: String) -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    
    // State variables
    var messageText by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("General") }
    var isPosting by remember { mutableStateOf(false) }
    
    val categories = listOf("General", "Infrastructure", "Safety", "Environment", "Other")
    val currentLocation by viewModel.currentLocation.collectAsState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "📍 Report Issue",
            style = MaterialTheme.typography.headlineMedium
        )
        
        // Location status
        if (currentLocation == null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                    Text(
                        "Getting your location...",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            LaunchedEffect(Unit) {
                viewModel.updateLocation()
            }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "📍 ${String.format("%.5f", currentLocation!!.latitude)}, ${String.format("%.5f", currentLocation!!.longitude)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
        
        // Message input
        OutlinedTextField(
            value = messageText,
            onValueChange = { if (it.length <= 300) messageText = it },
            label = { Text("Describe the issue in detail") },
            placeholder = { Text("e.g., Broken streetlight near the park entrance...") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            maxLines = 5,
            supportingText = {
                Text("${messageText.length}/300 characters")
            }
        )
        
        // Category selector
        var categoryExpanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            expanded = categoryExpanded,
            onExpandedChange = { categoryExpanded = !categoryExpanded }
        ) {
            TextField(
                readOnly = true,
                value = selectedCategory,
                onValueChange = {},
                label = { Text("Category") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                colors = ExposedDropdownMenuDefaults.textFieldColors(),
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = categoryExpanded,
                onDismissRequest = { categoryExpanded = false }
            ) {
                categories.forEach { category ->
                    DropdownMenuItem(
                        text = { Text(category) },
                        onClick = {
                            selectedCategory = category
                            categoryExpanded = false
                        }
                    )
                }
            }
        }
        
        // Tips card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    "💡 Tips for a good report:",
                    style = MaterialTheme.typography.labelLarge
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "• Be specific about the location\n• Describe what needs to be fixed\n• Mention any safety concerns",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        // AR Surface Placement Option (Phase I - Pokemon Go style!)
        if (onOpenARPlacement != null) {
            Divider(modifier = Modifier.padding(vertical = 8.dp))
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF4CAF50).copy(alpha = 0.1f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "🎯 OR Place on Surface (AR)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF4CAF50)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Use AR to anchor your message to a wall or floor",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            if (messageText.isBlank()) {
                                Toast.makeText(context, "Enter a message first", Toast.LENGTH_SHORT).show()
                            } else {
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                onOpenARPlacement(messageText, selectedCategory)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = messageText.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF4CAF50),
                            disabledContainerColor = Color.Gray
                        )
                    ) {
                        Text("🧱 PLACE ON SURFACE", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = {
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    onCancel()
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Cancel")
            }
            
            Button(
                onClick = {
                    if (messageText.isBlank()) {
                        Toast.makeText(context, "Please describe the issue", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    
                    if (currentLocation == null) {
                        Toast.makeText(context, "Location not available", Toast.LENGTH_SHORT).show()
                        viewModel.updateLocation()
                        return@Button
                    }
                    
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    isPosting = true
                    
                    scope.launch {
                        try {
                            val issueId = UUID.randomUUID().toString()
                            
                            // Create anchor data (no photo)
                            val anchorData = AnchorData(
                                id = issueId,
                                latitude = currentLocation!!.latitude,
                                longitude = currentLocation!!.longitude,
                                altitude = 0.0,
                                messageText = messageText,
                                category = selectedCategory.lowercase(),
                                timestamp = System.currentTimeMillis(),
                                wallAnchorId = "wall-${UUID.randomUUID()}"
                            )
                            
                            // Upload to Firebase Firestore
                            viewModel.uploadIssueSafely(anchorData)
                            
                            Toast.makeText(context, "Issue reported! 🎉", Toast.LENGTH_SHORT).show()
                            
                            // Wait a moment then navigate away
                            kotlinx.coroutines.delay(500)
                            onPostCreated()
                            
                        } catch (e: Exception) {
                            Logger.e(Logger.Category.AR, "Post failed", e)
                            isPosting = false
                            Toast.makeText(context, "Failed: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                },
                modifier = Modifier.weight(1f),
                enabled = messageText.isNotBlank() && currentLocation != null && !isPosting
            ) {
                if (isPosting) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Text("Posting...")
                    }
                } else {
                    Text("📤 Post Issue")
                }
            }
        }
    }
}
