package com.ryzix.vm.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ryzix.vm.qemu.QEMUBridge
import com.ryzix.vm.ui.theme.*
import com.ryzix.vm.viewmodel.VMViewModel

@Composable
fun SettingsScreen(
    viewModel: VMViewModel,
    onBack: () -> Unit
) {
    val qemuVersion by viewModel.qemuVersion.collectAsState()

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
                "Settings",
                color = RyzixOnSurface,
                fontWeight = FontWeight.SemiBold,
                fontSize = 17.sp
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // About section
            SettingsCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Ryzix VM", color = RyzixOnSurface, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("Version 1.0.0", color = RyzixOnSurfaceVariant, fontSize = 13.sp)
                    }
                    Icon(Icons.Default.Computer, contentDescription = null, tint = RyzixPrimary, modifier = Modifier.size(36.dp))
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Open-source Android VM powered by QEMU. Run Linux and custom OS images on your device.",
                    color = RyzixOnSurfaceVariant,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            }

            SectionHeader("QEMU Engine")
            SettingsCard {
                SettingsRow(
                    icon = Icons.Default.Memory,
                    title = "QEMU Version",
                    subtitle = qemuVersion
                )
                Divider(color = RyzixBorder, modifier = Modifier.padding(vertical = 8.dp))
                SettingsRow(
                    icon = Icons.Default.Architecture,
                    title = "Supported Architectures",
                    subtitle = "ARM64 (aarch64) • x86_64 • x86"
                )
            }

            SectionHeader("Storage")
            SettingsCard {
                SettingsRow(
                    icon = Icons.Default.Folder,
                    title = "VM Images Location",
                    subtitle = "/storage/emulated/0/RyzixVM/"
                )
                Divider(color = RyzixBorder, modifier = Modifier.padding(vertical = 8.dp))
                SettingsRow(
                    icon = Icons.Default.Download,
                    title = "Download Test OS",
                    subtitle = "Tiny Core Linux ARM64 (~100MB)"
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { /* TODO: trigger download */ },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = RyzixPrimary)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Download Test Image", fontSize = 13.sp)
                }
            }

            SectionHeader("Build Info")
            SettingsCard {
                SettingsRow(
                    icon = Icons.Default.Code,
                    title = "Source Code",
                    subtitle = "github.com/your-username/ryzix-vm"
                )
                Divider(color = RyzixBorder, modifier = Modifier.padding(vertical = 8.dp))
                SettingsRow(
                    icon = Icons.Default.Build,
                    title = "Build Type",
                    subtitle = if (qemuVersion.contains("Stub")) "Stub (APK only — QEMU via GitHub Actions)" else "Full QEMU Build"
                )
                Divider(color = RyzixBorder, modifier = Modifier.padding(vertical = 8.dp))
                SettingsRow(
                    icon = Icons.Default.Info,
                    title = "License",
                    subtitle = "GPL-2.0 (QEMU) • Apache 2.0 (App)"
                )
            }

            SectionHeader("First Time Setup")
            SettingsCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SetupStep(
                        step = "1",
                        title = "Build full APK",
                        desc = "Push to GitHub → Actions builds QEMU + APK automatically"
                    )
                    SetupStep(
                        step = "2",
                        title = "Download a Linux image",
                        desc = "Tiny Core Linux (~100MB) for first test, Debian 12 for full experience"
                    )
                    SetupStep(
                        step = "3",
                        title = "Create a VM",
                        desc = "Set image path, RAM, and CPU cores"
                    )
                    SetupStep(
                        step = "4",
                        title = "Start VM",
                        desc = "QEMU boots the OS, VNC display connects automatically"
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title.uppercase(),
        color = RyzixPrimary,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp
    )
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = RyzixSurface),
        content = {
            Column(modifier = Modifier.padding(16.dp)) { content() }
        }
    )
}

@Composable
private fun SettingsRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(icon, contentDescription = null, tint = RyzixPrimary, modifier = Modifier.size(20.dp))
        Column {
            Text(title, color = RyzixOnSurface, fontSize = 14.sp)
            Text(subtitle, color = RyzixOnSurfaceVariant, fontSize = 12.sp)
        }
    }
}

@Composable
private fun SetupStep(step: String, title: String, desc: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .background(RyzixPrimary.copy(alpha = 0.2f), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(step, color = RyzixPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
        Column {
            Text(title, color = RyzixOnSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(desc, color = RyzixOnSurfaceVariant, fontSize = 12.sp, lineHeight = 17.sp)
        }
    }
}
