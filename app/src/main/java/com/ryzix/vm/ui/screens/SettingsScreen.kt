package com.ryzix.vm.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ryzix.vm.ui.theme.*
import com.ryzix.vm.viewmodel.VMViewModel

@Composable
fun SettingsScreen(
    viewModel: VMViewModel,
    onBack: () -> Unit
) {
    val context         = LocalContext.current
    val qemuVersion     by viewModel.qemuVersion.collectAsState()
    val downloadProgress by viewModel.downloadProgress.collectAsState()
    val downloadMessage  by viewModel.downloadMessage.collectAsState()

    // Resolve the real save directory once (may differ by device/API level)
    val ryzixDir = remember { viewModel.getRyzixVMDir(context).absolutePath }

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
            // About
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
                    color = RyzixOnSurfaceVariant, fontSize = 13.sp, lineHeight = 18.sp
                )
            }

            SectionHeader("QEMU Engine")
            SettingsCard {
                SettingsRow(Icons.Default.Memory, "QEMU Version", qemuVersion)
                Divider(color = RyzixBorder, modifier = Modifier.padding(vertical = 8.dp))
                SettingsRow(Icons.Default.Architecture, "Supported Architectures", "ARM64 (aarch64) • x86_64 • x86")
            }

            SectionHeader("Storage & OS Download")
            SettingsCard {
                SettingsRow(
                    icon = Icons.Default.Folder,
                    title = "VM Images Location",
                    subtitle = ryzixDir
                )
                Divider(color = RyzixBorder, modifier = Modifier.padding(vertical = 8.dp))

                // Download description
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, tint = RyzixPrimary,
                        modifier = Modifier.size(20.dp).padding(top = 2.dp))
                    Column {
                        Text("Download & Setup Alpine Linux", color = RyzixOnSurface, fontSize = 14.sp)
                        Text(
                            "Downloads Alpine Linux virt x86_64 (~60 MB) to $ryzixDir " +
                            "and automatically creates a ready-to-boot VM. " +
                            "Just tap Start on the home screen after this finishes.",
                            color = RyzixOnSurfaceVariant, fontSize = 12.sp, lineHeight = 16.sp
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                if (downloadProgress != null) {
                    // ── Active download ────────────────────────────────────
                    val pct = ((downloadProgress ?: 0f) * 100).toInt()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Downloading…", color = RyzixOnSurface, fontSize = 13.sp,
                            fontWeight = FontWeight.Medium)
                        Text("$pct%", color = RyzixPrimary, fontSize = 14.sp,
                            fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { downloadProgress ?: 0f },
                        modifier = Modifier.fillMaxWidth().height(7.dp),
                        color = RyzixPrimary,
                        trackColor = RyzixBorder
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { viewModel.cancelDownload() },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = RyzixRed),
                        border = androidx.compose.foundation.BorderStroke(1.dp, RyzixRed.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.Default.Close, null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Cancel", fontSize = 13.sp)
                    }
                } else {
                    // ── Idle ──────────────────────────────────────────────
                    Button(
                        onClick = { viewModel.downloadAndSetup(context) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = RyzixPrimary)
                    ) {
                        Icon(Icons.Default.Download, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Download & Create VM", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }

                // ── Status / result message ────────────────────────────────
                AnimatedVisibility(visible = downloadMessage.isNotEmpty()) {
                    val isSuccess = downloadMessage.startsWith("Done")
                    val isError   = downloadMessage.startsWith("Failed") || downloadMessage.startsWith("Server")
                    val color = when { isSuccess -> RyzixGreen; isError -> RyzixRed; else -> RyzixOnSurfaceVariant }
                    val icon  = when { isSuccess -> Icons.Default.CheckCircle; isError -> Icons.Default.Error; else -> Icons.Default.Info }
                    Column {
                        Spacer(Modifier.height(10.dp))
                        Row(
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(icon, null, tint = color,
                                modifier = Modifier.size(16.dp).padding(top = 1.dp))
                            Text(downloadMessage, color = color, fontSize = 12.sp, lineHeight = 17.sp)
                        }
                    }
                }
            }

            SectionHeader("Build Info")
            SettingsCard {
                SettingsRow(Icons.Default.Code, "Source Code", "github.com/RD7890/ryzix-vm")
                Divider(color = RyzixBorder, modifier = Modifier.padding(vertical = 8.dp))
                SettingsRow(Icons.Default.Build, "Build Type",
                    if (qemuVersion.contains("not found")) "QEMU library missing"
                    else "Full QEMU Build (Limbo 5.1.0)")
                Divider(color = RyzixBorder, modifier = Modifier.padding(vertical = 8.dp))
                SettingsRow(Icons.Default.Info, "License", "GPL-2.0 (QEMU) • Apache 2.0 (App)")
            }

            SectionHeader("First Time Setup")
            SettingsCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SetupStep("1", "Download & Setup",
                        "Tap \"Download & Create VM\" above — it downloads Alpine Linux and creates a VM automatically.")
                    SetupStep("2", "Tap Start",
                        "Go to the home screen and tap Start on the Alpine Linux VM.")
                    SetupStep("3", "Wait for QEMU to boot",
                        "Alpine boots in about 30 seconds. VNC display connects automatically.")
                    SetupStep("4", "Install to disk (optional)",
                        "Run setup-alpine in the VM to install to a persistent disk image.")
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(title.uppercase(), color = RyzixPrimary, fontSize = 11.sp,
        fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = RyzixSurface)
    ) { Column(modifier = Modifier.padding(16.dp)) { content() } }
}

@Composable
private fun SettingsRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String
) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, null, tint = RyzixPrimary,
            modifier = Modifier.size(20.dp).padding(top = 2.dp))
        Column {
            Text(title, color = RyzixOnSurface, fontSize = 14.sp)
            Text(subtitle, color = RyzixOnSurfaceVariant, fontSize = 12.sp, lineHeight = 16.sp)
        }
    }
}

@Composable
private fun SetupStep(step: String, title: String, desc: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            modifier = Modifier.size(26.dp)
                .background(RyzixPrimary.copy(alpha = 0.2f), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) { Text(step, color = RyzixPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
        Column {
            Text(title, color = RyzixOnSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(desc, color = RyzixOnSurfaceVariant, fontSize = 12.sp, lineHeight = 17.sp)
        }
    }
}
