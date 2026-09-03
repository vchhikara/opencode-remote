package com.example

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.architecture.AppAction
import com.example.network.FileNode
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WorkspaceHostScreen(viewModel: MainViewModel, onAction: (AppAction) -> Unit) {
    var selectedFile by remember { mutableStateOf<FileNode?>(null) }
    
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isTablet = maxWidth >= 600.dp
        
        if (isTablet) {
            Row(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(1.2f)) {
                    ProjectChatScreen(viewModel, onAction)
                }
                VerticalDivider()
                Box(modifier = Modifier.weight(1f)) {
                    ProjectExplorerScreen(viewModel, onAction) { selectedFile = it }
                }
                VerticalDivider()
                Box(modifier = Modifier.weight(1.5f)) {
                    CodeViewerScreen(viewModel, onAction, selectedFile)
                }
            }
        } else {
            val pagerState = rememberPagerState(pageCount = { 3 })
            val coroutineScope = rememberCoroutineScope()
            
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                when (page) {
                    0 -> ProjectChatScreen(viewModel, onAction)
                    1 -> ProjectExplorerScreen(viewModel, onAction) { 
                        selectedFile = it
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(2)
                        }
                    }
                    2 -> CodeViewerScreen(viewModel, onAction, selectedFile)
                }
            }
        }
    }
}
