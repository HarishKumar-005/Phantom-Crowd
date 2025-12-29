package com.phantomcrowd.ui

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostIssueScreen(viewModel: MainViewModel) {
    var messageText by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf("General") }
    val categories = listOf("General", "Facility", "Safety")
    
    val currentLocation by viewModel.currentLocation.collectAsState()
    val context = LocalContext.current

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
            Text("POST ANONYMOUSLY")
        }
    }
}
