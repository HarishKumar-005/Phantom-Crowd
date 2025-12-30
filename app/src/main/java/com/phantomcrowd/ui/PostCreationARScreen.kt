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

// CameraX imports
import android.net.Uri
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.core.content.ContextCompat
import java.io.File
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun CameraCaptureScreen(
    onImageCaptured: (Uri) -> Unit,
    onError: (ImageCaptureException) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()

                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                    val capture = ImageCapture.Builder().build()
                    imageCapture = capture

                    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            capture
                        )
                    } catch (exc: Exception) {
                        Logger.e(Logger.Category.AR, "Use case binding failed", exc)
                    }
                }, ContextCompat.getMainExecutor(ctx))

                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        // Overlay UI
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start
            ) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(50))
                ) {
                    Text("✕", color = Color.White)
                }
            }

            Button(
                onClick = {
                    val capture = imageCapture ?: return@Button

                    val photoFile = File(
                        context.cacheDir,
                        "AR_ISSUE_${System.currentTimeMillis()}.jpg"
                    )

                    val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

                    capture.takePicture(
                        outputOptions,
                        ContextCompat.getMainExecutor(context),
                        object : ImageCapture.OnImageSavedCallback {
                            override fun onError(exc: ImageCaptureException) {
                                onError(exc)
                            }

                            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                onImageCaptured(Uri.fromFile(photoFile))
                            }
                        }
                    )
                },
                modifier = Modifier
                    .size(80.dp)
                    .background(Color.Transparent),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White)
            ) {
                // Shutter button style handled by shape/color above
            }
        }
    }
}

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
    var capturedPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var showCamera by remember { mutableStateOf(false) }
    
    val categories = listOf("General", "Infrastructure", "Safety", "Environment", "Other")
    val currentLocation by viewModel.currentLocation.collectAsState()
    
    if (showCamera) {
        CameraCaptureScreen(
            onImageCaptured = { uri ->
                capturedPhotoUri = uri
                showCamera = false
            },
            onError = { exc ->
                Toast.makeText(context, "Camera error: ${exc.message}", Toast.LENGTH_SHORT).show()
                showCamera = false
            },
            onClose = { showCamera = false }
        )
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
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
                "📍 Location: ${String.format("%.6f", currentLocation!!.latitude)}, ${String.format("%.6f", currentLocation!!.longitude)}",
                style = MaterialTheme.typography.bodySmall
            )
        }
        
        // Photo Preview Section
        if (capturedPhotoUri != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Gray)
            ) {
                // Load Bitmap from Uri
                val bitmap = remember(capturedPhotoUri) {
                    try {
                        val inputStream = context.contentResolver.openInputStream(capturedPhotoUri!!)
                        BitmapFactory.decodeStream(inputStream)
                    } catch (e: Exception) {
                        null
                    }
                }

                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Captured Photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                IconButton(
                    onClick = { capturedPhotoUri = null },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(50))
                ) {
                    Text("✕", color = Color.White)
                }
            }
        } else {
            OutlinedButton(
                onClick = { showCamera = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("📷 Take Photo")
            }
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
                        Toast.makeText(context, "Please enter a message", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    
                    if (currentLocation == null) {
                        Toast.makeText(context, "Location not available", Toast.LENGTH_SHORT).show()
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
                            
                            // Upload to Firebase
                            viewModel.uploadIssueSafely(anchorData, capturedPhotoUri)
                            
                            Toast.makeText(context, "Message posted!", Toast.LENGTH_SHORT).show()
                            
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
