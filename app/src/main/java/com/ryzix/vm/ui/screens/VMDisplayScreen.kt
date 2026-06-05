package com.ryzix.vm.ui.screens

import android.view.MotionEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ryzix.vm.model.VMConfig
import com.ryzix.vm.model.VMStatus
import com.ryzix.vm.ui.theme.*
import com.ryzix.vm.viewmodel.VMViewModel

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun VMDisplayScreen(
    viewModel: VMViewModel,
    config: VMConfig,
    onBack: () -> Unit
) {
    val vmStatus by viewModel.vmStatus.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val vncBitmap by viewModel.vncClient.bitmap.collectAsState()
    val vncConnected by viewModel.vncClient.connected.collectAsState()
    val serverWidth by viewModel.vncClient.serverWidth.collectAsState()
    val serverHeight by viewModel.vncClient.serverHeight.collectAsState()

    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var showControls by remember { mutableStateOf(true) }
    var showKeyboard by remember { mutableStateOf(false) }
    var keyInput by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // VM Display Canvas
        if (vncBitmap != null) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(0.5f, 4f)
                            offset += pan
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { tapOffset ->
                                if (vncConnected && serverWidth > 0 && serverHeight > 0) {
                                    val vmX = ((tapOffset.x - offset.x) / scale * serverWidth / size.width).toInt()
                                    val vmY = ((tapOffset.y - offset.y) / scale * serverHeight / size.height).toInt()
                                    viewModel.vncClient.sendPointerEvent(vmX, vmY, 1)
                                    viewModel.vncClient.sendPointerEvent(vmX, vmY, 0)
                                }
                                showControls = !showControls
                            },
                            onLongPress = { pressOffset ->
                                if (vncConnected && serverWidth > 0 && serverHeight > 0) {
                                    val vmX = ((pressOffset.x - offset.x) / scale * serverWidth / size.width).toInt()
                                    val vmY = ((pressOffset.y - offset.y) / scale * serverHeight / size.height).toInt()
                                    viewModel.vncClient.sendPointerEvent(vmX, vmY, 4)
                                    viewModel.vncClient.sendPointerEvent(vmX, vmY, 0)
                                }
                            }
                        )
                    }
            ) {
                drawIntoCanvas { canvas ->
                    val paint = Paint().apply { asFrameworkPaint() }
                    canvas.save()
                    canvas.translate(offset.x, offset.y)
                    canvas.scale(scale, scale)
                    canvas.drawImageRect(
                        image = vncBitmap!!.asImageBitmap(),
                        dstSize = androidx.compose.ui.geometry.Size(size.width, size.height)
                    )
                    canvas.restore()
                }
            }
        } else {
            // Loading state
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (vmStatus == VMStatus.RUNNING || vmStatus == VMStatus.STARTING) {
                    CircularProgressIndicator(color = RyzixPrimary, modifier = Modifier.size(48.dp))
                    Text(statusMessage, color = RyzixOnSurface, fontSize = 14.sp)
                    Text(
                        "Waiting for display...",
                        color = RyzixOnSurfaceVariant,
                        fontSize = 12.sp
                    )
                } else {
                    Icon(
                        Icons.Default.Computer,
                        contentDescription = null,
                        tint = RyzixOnSurfaceVariant,
                        modifier = Modifier.size(64.dp)
                    )
                    Text(config.name, color = RyzixOnSurface, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(statusMessage, color = RyzixOnSurfaceVariant, fontSize = 13.sp)
                }
            }
        }

        // Controls overlay
        if (showControls) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(config.name, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(
                                    when (vmStatus) {
                                        VMStatus.RUNNING -> RyzixGreen
                                        VMStatus.STARTING -> RyzixYellow
                                        else -> Color.Gray
                                    }
                                )
                        )
                        Text(
                            text = when (vmStatus) {
                                VMStatus.RUNNING -> if (vncConnected) "Connected" else "Running (no display)"
                                VMStatus.STARTING -> "Starting..."
                                else -> vmStatus.name.lowercase()
                            },
                            color = Color.Gray,
                            fontSize = 11.sp
                        )
                    }
                }
                Row {
                    IconButton(onClick = { scale = 1f; offset = Offset.Zero }) {
                        Icon(Icons.Default.FitScreen, contentDescription = "Reset zoom", tint = Color.White)
                    }
                }
            }

            // Bottom controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Ctrl+Alt+Del
                FilledTonalButton(
                    onClick = {
                        if (vncConnected) {
                            viewModel.vncClient.sendKeyEvent(0xFFE3, true)
                            viewModel.vncClient.sendKeyEvent(0xFFE9, true)
                            viewModel.vncClient.sendKeyEvent(0xFFFF, true)
                            viewModel.vncClient.sendKeyEvent(0xFFFF, false)
                            viewModel.vncClient.sendKeyEvent(0xFFE9, false)
                            viewModel.vncClient.sendKeyEvent(0xFFE3, false)
                        }
                    },
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = RyzixSurfaceVariant)
                ) {
                    Text("Ctrl+Alt+Del", color = Color.White, fontSize = 11.sp)
                }

                // Keyboard toggle
                IconButton(
                    onClick = { showKeyboard = !showKeyboard },
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (showKeyboard) RyzixPrimary else RyzixSurfaceVariant)
                ) {
                    Icon(Icons.Default.Keyboard, contentDescription = "Keyboard", tint = Color.White)
                }

                // ESC key
                FilledTonalButton(
                    onClick = {
                        if (vncConnected) {
                            viewModel.vncClient.sendKeyEvent(0xFF1B, true)
                            viewModel.vncClient.sendKeyEvent(0xFF1B, false)
                        }
                    },
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = RyzixSurfaceVariant)
                ) {
                    Text("ESC", color = Color.White, fontSize = 11.sp)
                }

                // Power
                IconButton(
                    onClick = { viewModel.stopVM(); onBack() },
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(RyzixRed.copy(alpha = 0.8f))
                ) {
                    Icon(Icons.Default.PowerSettingsNew, contentDescription = "Stop VM", tint = Color.White)
                }
            }
        }

        // Keyboard input overlay
        if (showKeyboard && vncConnected) {
            OutlinedTextField(
                value = keyInput,
                onValueChange = { newValue ->
                    val diff = newValue.length - keyInput.length
                    if (diff > 0) {
                        val newChar = newValue.last()
                        val keysym = newChar.code
                        viewModel.vncClient.sendKeyEvent(keysym, true)
                        viewModel.vncClient.sendKeyEvent(keysym, false)
                    } else if (diff < 0) {
                        // Backspace
                        viewModel.vncClient.sendKeyEvent(0xFF08, true)
                        viewModel.vncClient.sendKeyEvent(0xFF08, false)
                    }
                    keyInput = newValue
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 80.dp, start = 16.dp, end = 16.dp)
                    .background(Color.Black.copy(alpha = 0.9f), RoundedCornerShape(12.dp)),
                placeholder = { Text("Type here...", color = Color.Gray) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = RyzixPrimary,
                    unfocusedBorderColor = RyzixBorder
                ),
                singleLine = true
            )
        }
    }
}
