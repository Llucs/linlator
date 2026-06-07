package com.linlator.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.linlator.LinlatorApp
import com.linlator.XServerDisplayActivity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContainerDetailScreen(
    containerId: String,
    onBack: () -> Unit
) {
    val containerManager = LinlatorApp.instance.containerManager
    var config by remember { mutableStateOf(containerManager.get(containerId)) }
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(config?.name ?: "Container") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        if (config == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text("Container not found")
            }
            return@Scaffold
        }

        val container = config!!

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    DetailRow("Distribution", container.distro.replaceFirstChar { it.uppercase() })
                    DetailRow("Desktop", container.desktop.replaceFirstChar { it.uppercase() })
                    DetailRow("GPU Driver", container.graphicsDriver.replaceFirstChar { it.uppercase() })
                    DetailRow("Resolution", "${container.screenWidth}x${container.screenHeight}")
                }
            }

            Text("Actions", style = MaterialTheme.typography.titleMedium)

            ActionButton(
                icon = Icons.Filled.DesktopWindows,
                label = "Open Desktop",
                onClick = {
                    val intent = Intent(context, XServerDisplayActivity::class.java).apply {
                        putExtra("container_id", containerId)
                    }
                    context.startActivity(intent)
                }
            )

            ActionButton(
                icon = Icons.Filled.Terminal,
                label = "Terminal",
                onClick = { }
            )

            ActionButton(
                icon = Icons.Filled.Folder,
                label = "Files",
                onClick = { }
            )

            ActionButton(
                icon = Icons.Filled.PlayArrow,
                label = "Install App",
                onClick = { }
            )

            ActionButton(
                icon = Icons.Filled.Camera,
                label = "Snapshot",
                onClick = { }
            )

            Spacer(Modifier.height(16.dp))

            OutlinedButton(
                onClick = {
                    containerManager.delete(containerId)
                    onBack()
                },
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Delete, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Delete Container")
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ActionButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(56.dp)
    ) {
        Icon(icon, contentDescription = null)
        Spacer(Modifier.width(12.dp))
        Text(label)
    }
}
