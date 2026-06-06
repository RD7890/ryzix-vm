package com.ryzix.vm.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ryzix.vm.model.VMArch
import com.ryzix.vm.model.VMConfig
import com.ryzix.vm.ui.theme.*
import com.ryzix.vm.viewmodel.VMViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateVMScreen(
    viewModel: VMViewModel,
    onBack: () -> Unit,
    onCreated: () -> Unit
) {
    var vmName by remember { mutableStateOf("My VM") }
    var selectedArch by remember { mutableStateOf(VMArch.AARCH64) }
    var ramMB by remember { mutableStateOf(512f) }
    var cpuCores by remember { mutableStateOf(2f) }
    var bootFromCdrom by remember { mutableStateOf(true) }
    var diskImagePath by remember { mutableStateOf("") }
    var cdromImagePath by remember { mutableStateOf("") }
    var vncPort by remember { mutableStateOf("5900") }
    var extraArgs by remember { mutableStateOf("") }
    var showArchMenu by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    // Preset configs
    val presets = listOf(
        Triple("Tiny Core (100MB Test)", VMArch.AARCH64, 128),
        Triple("Alpine Linux", VMArch.AARCH64, 256),
        Triple("Debian 12 XFCE", VMArch.AARCH64, 1024),
        Triple("Custom x86_64", VMArch.X86_64, 512)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RyzixBackground)
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(RyzixSurface)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = RyzixOnSurface)
            }
            Text(
                "Create Virtual Machine",
                color = RyzixOnSurface,
                fontWeight = FontWeight.SemiBold,
                fontSize = 17.sp,
                modifier = Modifier.weight(1f)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Quick presets
            Text("Quick Presets", color = RyzixOnSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                presets.take(2).forEach { (name, arch, ram) ->
                    PresetChip(
                        label = name,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            vmName = name
                            selectedArch = arch
                            ramMB = ram.toFloat()
                        }
                    )
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                presets.drop(2).forEach { (name, arch, ram) ->
                    PresetChip(
                        label = name,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            vmName = name
                            selectedArch = arch
                            ramMB = ram.toFloat()
                        }
                    )
                }
            }

            Divider(color = RyzixBorder)

            // VM Name
            SectionLabel("VM Name")
            OutlinedTextField(
                value = vmName,
                onValueChange = { vmName = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Enter VM name") },
                colors = textFieldColors(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            // Architecture
            SectionLabel("Architecture")
            ExposedDropdownMenuBox(
                expanded = showArchMenu,
                onExpandedChange = { showArchMenu = it }
            ) {
                OutlinedTextField(
                    value = selectedArch.displayName,
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showArchMenu) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                    colors = textFieldColors(),
                    shape = RoundedCornerShape(12.dp)
                )
                ExposedDropdownMenu(
                    expanded = showArchMenu,
                    onDismissRequest = { showArchMenu = false },
                    modifier = Modifier.background(RyzixSurface)
                ) {
                    VMArch.values().forEach { arch ->
                        DropdownMenuItem(
                            text = { Text(arch.displayName, color = RyzixOnSurface) },
                            onClick = { selectedArch = arch; showArchMenu = false }
                        )
                    }
                }
            }

            // RAM
            SectionLabel("RAM: ${ramMB.toInt()} MB")
            Slider(
                value = ramMB,
                onValueChange = { ramMB = it },
                valueRange = 64f..4096f,
                steps = 0,
                colors = SliderDefaults.colors(thumbColor = RyzixPrimary, activeTrackColor = RyzixPrimary)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("64 MB", color = RyzixOnSurfaceVariant, fontSize = 11.sp)
                Text("4096 MB", color = RyzixOnSurfaceVariant, fontSize = 11.sp)
            }

            // CPU Cores
            SectionLabel("CPU Cores: ${cpuCores.toInt()}")
            Slider(
                value = cpuCores,
                onValueChange = { cpuCores = it },
                valueRange = 1f..8f,
                steps = 6,
                colors = SliderDefaults.colors(thumbColor = RyzixPrimary, activeTrackColor = RyzixPrimary)
            )

            // Disk image path
            SectionLabel("Disk Image Path (qcow2) — optional")
            OutlinedTextField(
                value = diskImagePath,
                onValueChange = { diskImagePath = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("/storage/emulated/0/RyzixVM/disk.qcow2", fontSize = 12.sp) },
                colors = textFieldColors(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            // CDROM path
            SectionLabel("CDROM / ISO Path — use the path from Settings download")
            OutlinedTextField(
                value = cdromImagePath,
                onValueChange = { cdromImagePath = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("/storage/emulated/0/RyzixVM/alpine-virt-3.19-x86_64.iso", fontSize = 11.sp) },
                colors = textFieldColors(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            // Boot from CDROM
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(RyzixSurface)
                    .clickable { bootFromCdrom = !bootFromCdrom }
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Boot from CDROM", color = RyzixOnSurface, fontSize = 14.sp)
                    Text("Boot ISO on first run", color = RyzixOnSurfaceVariant, fontSize = 12.sp)
                }
                Switch(
                    checked = bootFromCdrom,
                    onCheckedChange = { bootFromCdrom = it },
                    colors = SwitchDefaults.colors(checkedThumbColor = RyzixPrimary, checkedTrackColor = RyzixPrimary.copy(alpha = 0.4f))
                )
            }

            // VNC Port
            SectionLabel("VNC Port")
            OutlinedTextField(
                value = vncPort,
                onValueChange = { vncPort = it.filter { c -> c.isDigit() } },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("5900") },
                colors = textFieldColors(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            // Extra Args
            SectionLabel("Extra QEMU Arguments (optional)")
            OutlinedTextField(
                value = extraArgs,
                onValueChange = { extraArgs = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("-cpu cortex-a72 -display none") },
                colors = textFieldColors(),
                shape = RoundedCornerShape(12.dp),
                minLines = 2
            )

            Spacer(modifier = Modifier.height(80.dp))
        }

        // Create button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(RyzixBackground)
                .padding(16.dp)
        ) {
            Button(
                onClick = {
                    val config = VMConfig(
                        name = vmName.ifBlank { "Unnamed VM" },
                        arch = selectedArch,
                        ramMB = ramMB.toInt(),
                        cpuCores = cpuCores.toInt(),
                        diskImagePath = diskImagePath,
                        cdromImagePath = cdromImagePath,
                        bootFromCdrom = bootFromCdrom,
                        vncPort = vncPort.toIntOrNull() ?: 5900,
                        extraArgs = extraArgs
                    )
                    viewModel.addVM(config)
                    onCreated()
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = RyzixPrimary)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Create VM", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, color = RyzixOnSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.Medium)
}

@Composable
private fun PresetChip(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(RyzixSurface)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = RyzixOnSurface, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun textFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = RyzixOnSurface,
    unfocusedTextColor = RyzixOnSurface,
    focusedBorderColor = RyzixPrimary,
    unfocusedBorderColor = RyzixBorder,
    focusedPlaceholderColor = RyzixOnSurfaceVariant,
    unfocusedPlaceholderColor = RyzixOnSurfaceVariant,
    cursorColor = RyzixPrimary
)
