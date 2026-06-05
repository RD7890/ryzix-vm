package com.ryzix.vm.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ryzix.vm.model.VMArch
import com.ryzix.vm.model.VMConfig
import com.ryzix.vm.model.VMStatus
import com.ryzix.vm.ui.theme.*
import com.ryzix.vm.viewmodel.VMViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: VMViewModel,
    onStartVM: (VMConfig) -> Unit,
    onCreateVM: () -> Unit,
    onSettings: () -> Unit
) {
    val vmList by viewModel.vmList.collectAsState()
    val vmStatus by viewModel.vmStatus.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val qemuVersion by viewModel.qemuVersion.collectAsState()
    var showDeleteDialog by remember { mutableStateOf<VMConfig?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(RyzixBackground)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            listOf(RyzixSurface, RyzixSurfaceVariant)
                        )
                    )
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Ryzix VM",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = RyzixPrimary
                        )
                        Text(
                            text = qemuVersion,
                            fontSize = 11.sp,
                            color = RyzixOnSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(onClick = onSettings) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = RyzixOnSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Status bar
            if (statusMessage.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            when (vmStatus) {
                                VMStatus.RUNNING -> RyzixGreen.copy(alpha = 0.15f)
                                VMStatus.ERROR -> RyzixRed.copy(alpha = 0.15f)
                                VMStatus.STARTING -> RyzixYellow.copy(alpha = 0.15f)
                                else -> RyzixSurfaceVariant
                            }
                        )
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    when (vmStatus) {
                                        VMStatus.RUNNING -> RyzixGreen
                                        VMStatus.ERROR -> RyzixRed
                                        VMStatus.STARTING -> RyzixYellow
                                        else -> RyzixOnSurfaceVariant
                                    }
                                )
                        )
                        Text(
                            text = statusMessage,
                            color = RyzixOnSurface,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            // VM List
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        text = "Virtual Machines (${vmList.size})",
                        color = RyzixOnSurfaceVariant,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }

                if (vmList.isEmpty()) {
                    item {
                        EmptyVMCard(onCreateVM)
                    }
                } else {
                    items(vmList) { vm ->
                        VMCard(
                            config = vm,
                            isActive = viewModel.activeVM.collectAsState().value?.id == vm.id,
                            vmStatus = vmStatus,
                            onStart = { onStartVM(vm) },
                            onStop = { viewModel.stopVM() },
                            onDelete = { showDeleteDialog = vm }
                        )
                    }
                    item {
                        Spacer(modifier = Modifier.height(80.dp))
                    }
                }
            }
        }

        // FAB
        FloatingActionButton(
            onClick = onCreateVM,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp),
            containerColor = RyzixPrimary
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = "Create VM",
                tint = RyzixOnSurface
            )
        }
    }

    // Delete confirmation dialog
    showDeleteDialog?.let { vm ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            containerColor = RyzixSurface,
            title = { Text("Delete VM", color = RyzixOnSurface) },
            text = { Text("Delete \"${vm.name}\"? This cannot be undone.", color = RyzixOnSurfaceVariant) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removeVM(vm.id)
                    showDeleteDialog = null
                }) {
                    Text("Delete", color = RyzixRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) {
                    Text("Cancel", color = RyzixOnSurfaceVariant)
                }
            }
        )
    }
}

@Composable
fun VMCard(
    config: VMConfig,
    isActive: Boolean,
    vmStatus: VMStatus,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onDelete: () -> Unit
) {
    val isRunning = isActive && vmStatus == VMStatus.RUNNING
    val isStarting = isActive && vmStatus == VMStatus.STARTING

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (isActive) 1.dp else 0.dp,
                color = if (isActive) RyzixPrimary else RyzixBorder,
                shape = RoundedCornerShape(16.dp)
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = RyzixSurface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(RyzixPrimary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Computer,
                            contentDescription = null,
                            tint = RyzixPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column {
                        Text(
                            text = config.name,
                            color = RyzixOnSurface,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "${config.arch.displayName} • ${config.ramMB}MB RAM • ${config.cpuCores} CPU",
                            color = RyzixOnSurfaceVariant,
                            fontSize = 12.sp
                        )
                    }
                }

                // Status dot
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                isRunning -> RyzixGreen
                                isStarting -> RyzixYellow
                                else -> RyzixOnSurfaceVariant.copy(alpha = 0.3f)
                            }
                        )
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!isActive || vmStatus == VMStatus.STOPPED || vmStatus == VMStatus.ERROR) {
                    Button(
                        onClick = onStart,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = RyzixPrimary)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Start", fontSize = 13.sp)
                    }
                } else {
                    Button(
                        onClick = onStop,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = RyzixRed.copy(alpha = 0.8f)),
                        enabled = !isStarting
                    ) {
                        if (isStarting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = RyzixOnSurface,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                        Spacer(Modifier.width(4.dp))
                        Text(if (isStarting) "Starting..." else "Stop", fontSize = 13.sp)
                    }
                }

                OutlinedButton(
                    onClick = onDelete,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = RyzixRed),
                    border = androidx.compose.foundation.BorderStroke(1.dp, RyzixRed.copy(alpha = 0.5f))
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
fun EmptyVMCard(onCreate: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(RyzixSurface)
            .border(1.dp, RyzixBorder, RoundedCornerShape(16.dp))
            .clickable(onClick = onCreate)
            .padding(40.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Default.AddCircleOutline,
                contentDescription = null,
                tint = RyzixOnSurfaceVariant,
                modifier = Modifier.size(48.dp)
            )
            Text(
                text = "No VMs yet",
                color = RyzixOnSurface,
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp
            )
            Text(
                text = "Tap to create your first virtual machine",
                color = RyzixOnSurfaceVariant,
                fontSize = 13.sp
            )
        }
    }
}
