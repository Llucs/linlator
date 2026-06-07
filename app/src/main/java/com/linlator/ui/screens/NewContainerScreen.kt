package com.linlator.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.linlator.LinlatorApp
import com.linlator.container.ContainerConfig
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NewContainerScreen(
    onCreated: () -> Unit,
    onBack: () -> Unit
) {
    val containerManager = LinlatorApp.instance.containerManager

    var name by remember { mutableStateOf("") }
    var selectedDistro by remember { mutableStateOf("ubuntu") }
    var selectedDesktop by remember { mutableStateOf("xfce4") }
    var selectedGpu by remember { mutableStateOf("virgl") }
    var screenWidth by remember { mutableStateOf("1280") }
    var screenHeight by remember { mutableStateOf("720") }
    var error by remember { mutableStateOf<String?>(null) }

    val distros = listOf("alpine", "debian", "ubuntu")
    val desktops = listOf("openbox", "xfce4", "lxde", "fluxbox")
    val gpuDrivers = listOf("zink", "gl4es", "virgl")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New Container") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Container Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Text("Distribution", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                distros.forEach { distro ->
                    FilterChip(
                        selected = selectedDistro == distro,
                        onClick = { selectedDistro = distro },
                        label = { Text(distro.replaceFirstChar { it.uppercase() }) }
                    )
                }
            }

            Text("Desktop Environment", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                desktops.forEach { desktop ->
                    FilterChip(
                        selected = selectedDesktop == desktop,
                        onClick = { selectedDesktop = desktop },
                        label = { Text(desktop.replaceFirstChar { it.uppercase() }) }
                    )
                }
            }

            Text("GPU Driver", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                gpuDrivers.forEach { gpu ->
                    FilterChip(
                        selected = selectedGpu == gpu,
                        onClick = { selectedGpu = gpu },
                        label = { Text(gpu.replaceFirstChar { it.uppercase() }) }
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = screenWidth,
                    onValueChange = { screenWidth = it },
                    label = { Text("Width") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = screenHeight,
                    onValueChange = { screenHeight = it },
                    label = { Text("Height") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
            }

            if (error != null) {
                Text(
                    text = error!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(Modifier.weight(1f))

            Button(
                onClick = {
                    if (name.isBlank()) {
                        error = "Container name is required"
                        return@Button
                    }
                    val w = screenWidth.toIntOrNull() ?: 1280
                    val h = screenHeight.toIntOrNull() ?: 720
                    val config = ContainerConfig(
                        id = UUID.randomUUID().toString().take(8),
                        name = name.trim(),
                        distro = selectedDistro,
                        screenWidth = w,
                        screenHeight = h,
                        desktop = selectedDesktop,
                        graphicsDriver = selectedGpu
                    )
                    containerManager.create(config)
                    onCreated()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Create Container")
            }
        }
    }
}
