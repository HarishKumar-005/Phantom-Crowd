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
import com.phantomcrowd.data.SurfaceAnchor
import com.phantomcrowd.data.SurfaceAnchorManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostIssueScreen(
    viewModel: MainViewModel,
    onOpenARPlacement: ((messageText: String, category: String) -> Unit)? = null
) {
    var messageText by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf("General") }
    val categories = listOf("General", "Facility", "Safety", "Event", "Social")
    
    val currentLocation by viewModel.currentLocation.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Post New Issue", style = MaterialTheme.typography.headlineMedium)
        
        Spacer(modifier = Modifier.height(24.dp))
        
        if (currentLocation == null) {
            Button(
                onClick = { 
                    android.util.Log.d("GPS", "Get GPS Location button clicked!")
                    android.widget.Toast.makeText(context, "Getting location...", android.widget.Toast.LENGTH_SHORT).show()
                    viewModel.updateLocation() 
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Get GPS Location")
            }
            Text("Tap button to get your current location", style = MaterialTheme.typography.bodySmall)
        } else {
            Text("📍 Location: ${String.format("%.6f", currentLocation?.latitude)}, ${String.format("%.6f", currentLocation?.longitude)}")
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = messageText,
            onValueChange = { if (it.length <= 200) messageText = it },
            label = { Text("Message (max 200 chars)") },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 3
        )

        Spacer(modifier = Modifier.height(16.dp))

        Box(modifier = Modifier.fillMaxWidth()) {
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
            ) {
                TextField(
                    readOnly = true,
                    value = selectedCategory,
                    onValueChange = { },
                    label = { Text("Category") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    colors = ExposedDropdownMenuDefaults.textFieldColors(),
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    categories.forEach { selectionOption ->
                        DropdownMenuItem(
                            text = { Text(selectionOption) },
                            onClick = {
                                selectedCategory = selectionOption
                                expanded = false
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                if (currentLocation != null && messageText.isNotBlank()) {
                     viewModel.postIssue(messageText, selectedCategory) {
                         Toast.makeText(context, "Issue Posted!", Toast.LENGTH_SHORT).show()
                         messageText = ""
                     }
                } else {
                     Toast.makeText(context, "Waiting for location...", Toast.LENGTH_SHORT).show()
                     viewModel.updateLocation()
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = currentLocation != null && messageText.isNotBlank()
        ) {
            Text("📍 POST ANONYMOUSLY (GPS)")
        }
        
        // AR Placement Section
        Spacer(modifier = Modifier.height(16.dp))
        
        Divider()
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "🎯 OR Place on Surface (AR)",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        
        Text(
            text = "Use AR to place your message on a wall or floor for precise accuracy",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Button(
            onClick = {
                if (messageText.isNotBlank()) {
                    onOpenARPlacement?.invoke(messageText, selectedCategory)
                        ?: Toast.makeText(context, "AR Placement not available", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Enter a message first", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = messageText.isNotBlank(),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF4CAF50)
            )
        ) {
            Text("🧱 PLACE ON SURFACE (AR)")
        }
    }
}
