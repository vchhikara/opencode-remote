package com.example

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import com.composeunstyled.UnstyledSwitch
import com.composeunstyled.SwitchThumb
import com.example.architecture.*
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onAction: (AppAction) -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", color = TextPrimary, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { onAction(NavigationAction.NavigateBack) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppBackground)
            )
        },
        containerColor = AppBackground
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item { SettingsSectionTitle("Connection") }
            item { SettingsSwitch("Auto Discovery (mDNS / NSD)", true) }
            item { SettingsSwitch("Auto Reconnect", true) }
            item { SettingsAction("Test Connection", "Verify connection to OpenCode server") }

            item { SettingsSectionTitle("Appearance") }
            item { SettingsSwitch("Dynamic Colors", true) }
            item { SettingsSwitch("AMOLED Mode", false) }
            
            item { SettingsSectionTitle("AI") }
            item { SettingsSwitch("Streaming Responses", true) }
            item { SettingsSwitch("Conversation History", true) }
            item { SettingsAction("Default Mode", "Build") }

            item { SettingsSectionTitle("Editor") }
            item { SettingsSwitch("Syntax Highlighting", true) }
            item { SettingsSwitch("Line Numbers", true) }
            item { SettingsSwitch("Word Wrap", false) }
            
            item { SettingsSectionTitle("Notifications") }
            item { SettingsSwitch("Build Completed", true) }
            item { SettingsSwitch("AI Finished", true) }
            item { SettingsSwitch("Error Notifications", true) }
            
            item { SettingsSectionTitle("Developer") }
            item { SettingsSwitch("Enable Debug Mode", false) }
            item { SettingsAction("App Version", "1.0.0") }
            item { SettingsAction("Protocol Version", "v1.2") }
        }
    }
}

@Composable
fun SettingsSectionTitle(title: String) {
    Text(
        text = title,
        color = Primary,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
fun SettingsSwitch(title: String, initialValue: Boolean) {
    var checked by remember { mutableStateOf(initialValue) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(title, color = TextPrimary, style = MaterialTheme.typography.bodyLarge)
        UnstyledSwitch(
            checked = checked,
            onCheckedChange = { checked = it },
            modifier = Modifier
                .width(48.dp)
                .height(28.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(if (checked) Primary else SurfaceVariant)
                .padding(3.dp)
        ) {
            SwitchThumb(
                modifier = Modifier
                    .size(22.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (checked) Color.White else TextSecondary)
            )
        }
    }
}

@Composable
fun SettingsAction(title: String, subtitle: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { }
            .padding(vertical = 8.dp)
    ) {
        Text(title, color = TextPrimary, style = MaterialTheme.typography.bodyLarge)
        Text(subtitle, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
    }
}
