package com.phantomcrowd.ui

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.phantomcrowd.data.AnchorData
import com.phantomcrowd.utils.Logger
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Modern SceneView-ready Posting Screen.
 * Currently uses Form-based input to ensure stability during migration.
 * AR Camera view will be re-enabled in Phase G using SceneView 2.0.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostCreationARScreen(
    viewModel: MainViewModel,
    onPostCreated: () -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // State variables
    var messageText by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("General") }
    var isPosting by remember { mutableStateOf(false) }
    
    val categories = listOf("General", "Infrastructure", "Safety", "Environment", "Other")
    val currentLocation by viewModel.currentLocation.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Post Message (Modern AR)",
                style = MaterialTheme.typography.headlineMedium
            )
        
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "✨ AR Engine Updated",
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    "Project migrated to SceneView 2.0. AR Camera view will be visible in Phase G.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        
            Spacer(modifier = Modifier.height(8.dp))

            if (currentLocation == null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "📍 Getting location...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    if (errorMessage?.contains("GPS") == true) {
                        Button(onClick = { viewModel.updateLocation() }) {
                            Text("Retry Location")
                        }
                    }
                }
                LaunchedEffect(Unit) {
                    viewModel.updateLocation()
                }
            } else {
                Text(
                    "📍 Location: ${String.format("%.6f", currentLocation!!.latitude)}, ${String.format("%.6f", currentLocation!!.longitude)}",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            OutlinedTextField(
            value = messageText,
            onValueChange = { if (it.length <= 200) messageText = it },
            label = { Text("Message") },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 3,
            supportingText = {
                Text("${messageText.length}/200", style = MaterialTheme.typography.bodySmall)
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
        
            Spacer(modifier = Modifier.weight(1f))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancel")
                }

                Button(
                    onClick = {
                        if (messageText.isBlank()) {
                            scope.launch { snackbarHostState.showSnackbar("Please enter a message") }
                            return@Button
                        }

                        if (currentLocation == null) {
                            scope.launch { snackbarHostState.showSnackbar("Location not available") }
                            viewModel.updateLocation()
                            return@Button
                        }

                        isPosting = true

                        scope.launch {
                            try {
                                // Create anchor data
                                val anchorData = AnchorData(
                                    id = UUID.randomUUID().toString(),
                                    latitude = currentLocation!!.latitude,
                                    longitude = currentLocation!!.longitude,
                                    altitude = 0.0,
                                    messageText = messageText,
                                    category = selectedCategory.lowercase(),
                                    timestamp = System.currentTimeMillis(),
                                    wallAnchorId = "wall-${UUID.randomUUID()}"
                                )

                                // Upload to Firebase and wait for result
                                val result = viewModel.uploadIssueSafely(anchorData)

                                if (result.isSuccess) {
                                    snackbarHostState.showSnackbar("Message posted!")
                                    // Wait a moment then navigate away
                                    kotlinx.coroutines.delay(500)
                                    onPostCreated()
                                } else {
                                    val error = result.exceptionOrNull()
                                    isPosting = false
                                    snackbarHostState.showSnackbar("Failed: ${error?.message ?: "Unknown error"}")
                                }

                            } catch (e: Exception) {
                                Logger.e(Logger.Category.AR, "Post failed", e)
                                isPosting = false
                                snackbarHostState.showSnackbar("Failed: ${e.message}")
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = messageText.isNotBlank() && currentLocation != null && !isPosting
                ) {
                    if (isPosting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text("Post Message")
                    }
                }
            }
        }
    }
}
