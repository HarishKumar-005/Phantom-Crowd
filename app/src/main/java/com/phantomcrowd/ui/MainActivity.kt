package com.phantomcrowd.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.phantomcrowd.ui.theme.PhantomCrowdTheme

class MainActivity : ComponentActivity() {
    
    // Simple ViewModel instance
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            PhantomCrowdTheme {
                MainScreen(viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Nearby", "Post", "AR View")
    
    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, title ->
                    NavigationBarItem(
                        icon = { 
                            when(index) {
                                0 -> Icon(Icons.Filled.List, contentDescription = null)
                                1 -> Icon(Icons.Filled.Add, contentDescription = null)
                                else -> Icon(Icons.Filled.Home, contentDescription = null) // Using Home for AR View temporarily
                            }
                        },
                        label = { Text(title) },
                        selected = selectedTab == index,
                        onClick = { selectedTab = index }
                    )
                }
            }
        }
    ) { paddingValues ->
        Surface(modifier = Modifier.padding(paddingValues)) {
            when(selectedTab) {
                0 -> NearbyIssuesScreen(viewModel)
                1 -> PostIssueScreen(viewModel)
                2 -> ARViewScreen(viewModel)
            }
        }
    }
}
