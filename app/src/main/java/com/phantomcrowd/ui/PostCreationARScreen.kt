package com.phantomcrowd.ui

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.phantomcrowd.data.AnchorData
import com.phantomcrowd.utils.Logger
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File
import java.util.UUID

/**
 * Modern SceneView-ready Posting Screen.
 * Features:
 * - Form-based input for message and category
 * - Photo capture using device camera
 * - Firebase Storage upload for photos
 * - Location-based posting
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
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    
    // State variables
    var messageText by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("General") }
    var isPosting by remember { mutableStateOf(false) }
    var photoUri by remember { mutableStateOf<Uri?>(null) }
    var isUploadingPhoto by remember { mutableStateOf(false) }
    
    val categories = listOf("General", "Infrastructure", "Safety", "Environment", "Other")
    val currentLocation by viewModel.currentLocation.collectAsState()
    
    // Create temp file for photo
    val tempPhotoFile = remember {
        File.createTempFile("issue_photo_", ".jpg", context.cacheDir)
    }
    val tempPhotoUri = remember {
        androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            tempPhotoFile
        )
    }
    
    // Camera launcher
    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            photoUri = tempPhotoUri
            Logger.d(Logger.Category.AR, "Photo captured: $photoUri")
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "📍 Report Issue",
            style = MaterialTheme.typography.headlineMedium
        )
        
        // Location status
        if (currentLocation == null) {
            Text(
                "📍 Getting location...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
            LaunchedEffect(Unit) {
                viewModel.updateLocation()
            }
        } else {
            Text(
                "📍 ${String.format("%.5f", currentLocation!!.latitude)}, ${String.format("%.5f", currentLocation!!.longitude)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
        }
        
        // Photo section
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (photoUri != null) {
                    // Show captured photo
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                    ) {
                        Image(
                            painter = rememberAsyncImagePainter(photoUri),
                            contentDescription = "Captured photo",
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                        
                        // Remove photo button
                        IconButton(
                            onClick = { photoUri = null },
                            modifier = Modifier.align(Alignment.TopEnd)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Remove photo",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                } else {
                    // Take photo button
                    OutlinedButton(
                        onClick = {
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            takePictureLauncher.launch(tempPhotoUri)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                            .border(
                                2.dp,
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                RoundedCornerShape(8.dp)
                            )
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "📷",
                                style = MaterialTheme.typography.headlineLarge
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Take Photo")
                        }
                    }
                }
                
                if (photoUri != null) {
                    Text(
                        "✅ Photo attached",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Text(
                        "Optional: Add a photo of the issue",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        
        // Message input
        OutlinedTextField(
            value = messageText,
            onValueChange = { if (it.length <= 200) messageText = it },
            label = { Text("Describe the issue") },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 3,
            supportingText = {
                Text("${messageText.length}/200")
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
                            var photoUrl = ""
                            
                            // Upload photo if present
                            if (photoUri != null) {
                                isUploadingPhoto = true
                                try {
                                    photoUrl = uploadPhotoToFirebase(context, photoUri!!, issueId)
                                    Logger.d(Logger.Category.DATA, "Photo uploaded: $photoUrl")
                                } catch (e: Exception) {
                                    Logger.e(Logger.Category.DATA, "Photo upload failed", e)
                                    // Continue without photo
                                }
                                isUploadingPhoto = false
                            }
                            
                            // Create anchor data
                            val anchorData = AnchorData(
                                id = issueId,
                                latitude = currentLocation!!.latitude,
                                longitude = currentLocation!!.longitude,
                                altitude = 0.0,
                                messageText = messageText,
                                category = selectedCategory.lowercase(),
                                timestamp = System.currentTimeMillis(),
                                wallAnchorId = "wall-${UUID.randomUUID()}",
                                photoUrl = photoUrl
                            )
                            
                            // Upload to Firebase
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
                        Text(if (isUploadingPhoto) "Uploading..." else "Posting...")
                    }
                } else {
                    Text("📤 Post Issue")
                }
            }
        }
    }
}

/**
 * Upload photo to Firebase Storage
 */
private suspend fun uploadPhotoToFirebase(context: Context, uri: Uri, issueId: String): String {
    val storage = FirebaseStorage.getInstance()
    val ref = storage.reference.child("issues/$issueId/photo.jpg")
    
    val inputStream = context.contentResolver.openInputStream(uri)
        ?: throw Exception("Cannot read photo file")
    
    val uploadTask = ref.putStream(inputStream).await()
    return ref.downloadUrl.await().toString()
}
