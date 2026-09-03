package com.example

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.architecture.AppAction
import com.example.architecture.NavigationAction
import com.example.network.DiscoveredService
import com.example.network.NsdHelper
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.AppBackground
import com.example.ui.theme.Primary
import com.example.ui.theme.Surface
import com.example.ui.theme.SurfaceVariant
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicesScreen(onAction: (AppAction) -> Unit) {
    val context = LocalContext.current
    val nsdHelper = remember { NsdHelper(context) }
    val devices by nsdHelper.discoveredServices.collectAsStateWithLifecycle()

    DisposableEffect(Unit) {
        nsdHelper.startDiscovery()
        onDispose {
            nsdHelper.stopDiscovery()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Available Workspaces", fontWeight = FontWeight.Bold, color = TextPrimary) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppBackground),
                navigationIcon = {
                    IconButton(onClick = { onAction(NavigationAction.NavigateBack) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Primary)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        nsdHelper.stopDiscovery()
                        nsdHelper.startDiscovery()
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Primary)
                    }
                }
            )
        },
        containerColor = AppBackground
    ) { padding ->
        if (devices.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Primary)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Searching for OpenCode on local network...", color = TextSecondary)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(devices) { device ->
                    DeviceItem(
                        device = device, 
                        onClick = {
                            onAction(com.example.architecture.PairingAction.ConnectManually(device.host))
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun DeviceItem(device: DiscoveredService, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = Surface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(SurfaceVariant, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Computer, contentDescription = null, tint = Primary)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(device.name, color = TextPrimary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text("${device.host}:${device.port}", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
