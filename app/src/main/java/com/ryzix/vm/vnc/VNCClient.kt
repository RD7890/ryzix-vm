package com.ryzix.vm.vnc

import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.Socket

class VNCClient {

    private val TAG = "VNCClient"

    private val _bitmap = MutableStateFlow<Bitmap?>(null)
    val bitmap: StateFlow<Bitmap?> = _bitmap

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected

    private val _serverWidth = MutableStateFlow(1024)
    val serverWidth: StateFlow<Int> = _serverWidth

    private val _serverHeight = MutableStateFlow(768)
    val serverHeight: StateFlow<Int> = _serverHeight

    private var socket: Socket? = null
    private var input: DataInputStream? = null
    private var output: DataOutputStream? = null

    external fun nativeConnect(host: String, port: Int): Boolean
    external fun nativeDisconnect()
    external fun nativeIsConnected(): Boolean
    external fun nativeSendPointerEvent(x: Int, y: Int, buttonMask: Int)
    external fun nativeSendKeyEvent(keysym: Int, down: Boolean)
    external fun nativeSendFramebufferUpdateRequest(x: Int, y: Int, width: Int, height: Int, incremental: Boolean)

    companion object {
        init {
            System.loadLibrary("ryzixvm")
        }
    }

    suspend fun connect(host: String, port: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "Connecting to VNC $host:$port")
            socket = Socket(host, port)
            input = DataInputStream(socket!!.getInputStream())
            output = DataOutputStream(socket!!.getOutputStream())

            if (performHandshake()) {
                _connected.value = true
                Log.i(TAG, "VNC connected: ${_serverWidth.value}x${_serverHeight.value}")
                true
            } else {
                disconnect()
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Connection failed: ${e.message}")
            false
        }
    }

    private fun performHandshake(): Boolean {
        return try {
            val inp = input ?: return false
            val out = output ?: return false

            // Read server version
            val versionBytes = ByteArray(12)
            inp.readFully(versionBytes)
            val serverVersion = String(versionBytes).trim()
            Log.i(TAG, "Server version: $serverVersion")

            // Send client version
            out.write("RFB 003.008\n".toByteArray())
            out.flush()

            // Read number of security types
            val numSecTypes = inp.readUnsignedByte()
            val secTypes = ByteArray(numSecTypes)
            inp.readFully(secTypes)
            Log.i(TAG, "Security types: ${secTypes.toList()}")

            // Choose None (1) or VNC auth (2)
            val chosenType: Byte = if (secTypes.contains(1)) 1 else secTypes[0]
            out.write(byteArrayOf(chosenType))
            out.flush()

            if (chosenType == 2.toByte()) {
                // VNC auth — read challenge
                val challenge = ByteArray(16)
                inp.readFully(challenge)
                // For now send empty response (no password)
                out.write(ByteArray(16))
                out.flush()
            }

            // Security result
            val secResult = inp.readInt()
            if (secResult != 0) {
                Log.e(TAG, "Security handshake failed: $secResult")
                return false
            }

            // ClientInit — shared=1
            out.write(byteArrayOf(1))
            out.flush()

            // ServerInit
            val fbWidth = inp.readUnsignedShort()
            val fbHeight = inp.readUnsignedShort()
            _serverWidth.value = fbWidth
            _serverHeight.value = fbHeight

            // Skip pixel format (16 bytes)
            val pixelFormat = ByteArray(16)
            inp.readFully(pixelFormat)

            // Read desktop name
            val nameLength = inp.readInt()
            val nameBytes = ByteArray(nameLength)
            inp.readFully(nameBytes)
            Log.i(TAG, "Desktop: ${String(nameBytes)}, Size: ${fbWidth}x${fbHeight}")

            // Create initial bitmap
            _bitmap.value = Bitmap.createBitmap(fbWidth, fbHeight, Bitmap.Config.ARGB_8888)

            // Set pixel format to use (request 32bpp BGRA)
            sendSetPixelFormat()

            true
        } catch (e: Exception) {
            Log.e(TAG, "Handshake failed: ${e.message}")
            false
        }
    }

    private fun sendSetPixelFormat() {
        val out = output ?: return
        val msg = ByteArray(20)
        msg[0] = 0 // SetPixelFormat
        // 3 bytes padding
        // pixel format: 32bpp, 24 depth, little-endian, true-color
        msg[4] = 32 // bits-per-pixel
        msg[5] = 24 // depth
        msg[6] = 0  // big-endian = false
        msg[7] = 1  // true-colour = true
        // Red: max=255, shift=16
        msg[8] = 0; msg[9] = 255.toByte()
        // Green: max=255, shift=8
        msg[10] = 0; msg[11] = 255.toByte()
        // Blue: max=255, shift=0
        msg[12] = 0; msg[13] = 255.toByte()
        msg[14] = 16 // red-shift
        msg[15] = 8  // green-shift
        msg[16] = 0  // blue-shift
        out.write(msg)
        out.flush()
    }

    suspend fun requestFramebufferUpdate(incremental: Boolean = true) = withContext(Dispatchers.IO) {
        try {
            val out = output ?: return@withContext
            val w = _serverWidth.value
            val h = _serverHeight.value

            val msg = ByteArray(10)
            msg[0] = 3 // FramebufferUpdateRequest
            msg[1] = if (incremental) 1 else 0
            // x=0, y=0
            msg[6] = (w shr 8).toByte()
            msg[7] = (w and 0xFF).toByte()
            msg[8] = (h shr 8).toByte()
            msg[9] = (h and 0xFF).toByte()
            out.write(msg)
            out.flush()

            processServerMessage()
        } catch (e: Exception) {
            Log.e(TAG, "Frame request failed: ${e.message}")
        }
    }

    private fun processServerMessage() {
        val inp = input ?: return
        when (val msgType = inp.readUnsignedByte()) {
            0 -> handleFramebufferUpdate(inp)
            2 -> {
                inp.skip(1) // padding
                val bell = inp.readUnsignedByte() // not used
            }
            else -> Log.w(TAG, "Unknown server message type: $msgType")
        }
    }

    private fun handleFramebufferUpdate(inp: DataInputStream) {
        inp.skip(1) // padding
        val numRects = inp.readUnsignedShort()
        val bmp = _bitmap.value ?: return

        repeat(numRects) {
            val x = inp.readUnsignedShort()
            val y = inp.readUnsignedShort()
            val w = inp.readUnsignedShort()
            val h = inp.readUnsignedShort()
            val encoding = inp.readInt()

            when (encoding) {
                0 -> { // Raw
                    val pixels = IntArray(w * h)
                    for (i in pixels.indices) {
                        val b = inp.readUnsignedByte()
                        val g = inp.readUnsignedByte()
                        val r = inp.readUnsignedByte()
                        inp.readUnsignedByte() // padding
                        pixels[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                    }
                    bmp.setPixels(pixels, 0, w, x, y, w, h)
                    _bitmap.value = bmp
                }
                2 -> { // RRE - simple fill
                    val numSubRects = inp.readInt()
                    val bgPixel = inp.readInt()
                    val bgColor = bgPixel or (0xFF shl 24)
                    for (px in x until x + w) {
                        for (py in y until y + h) {
                            if (px < bmp.width && py < bmp.height)
                                bmp.setPixel(px, py, bgColor)
                        }
                    }
                    repeat(numSubRects) {
                        val subPixel = inp.readInt()
                        val sx = inp.readUnsignedShort() + x
                        val sy = inp.readUnsignedShort() + y
                        val sw = inp.readUnsignedShort()
                        val sh = inp.readUnsignedShort()
                        val subColor = subPixel or (0xFF shl 24)
                        for (px in sx until sx + sw) {
                            for (py in sy until sy + sh) {
                                if (px < bmp.width && py < bmp.height)
                                    bmp.setPixel(px, py, subColor)
                            }
                        }
                    }
                    _bitmap.value = bmp
                }
                else -> Log.w(TAG, "Unsupported encoding: $encoding")
            }
        }
    }

    fun sendPointerEvent(x: Int, y: Int, buttonMask: Int) {
        try {
            val out = output ?: return
            val msg = ByteArray(6)
            msg[0] = 5
            msg[1] = buttonMask.toByte()
            msg[2] = (x shr 8).toByte()
            msg[3] = (x and 0xFF).toByte()
            msg[4] = (y shr 8).toByte()
            msg[5] = (y and 0xFF).toByte()
            out.write(msg)
            out.flush()
        } catch (e: Exception) {
            Log.e(TAG, "Pointer event failed: ${e.message}")
        }
    }

    fun sendKeyEvent(keysym: Int, down: Boolean) {
        try {
            val out = output ?: return
            val msg = ByteArray(8)
            msg[0] = 4
            msg[1] = if (down) 1 else 0
            msg[4] = (keysym shr 24).toByte()
            msg[5] = (keysym shr 16).toByte()
            msg[6] = (keysym shr 8).toByte()
            msg[7] = (keysym and 0xFF).toByte()
            out.write(msg)
            out.flush()
        } catch (e: Exception) {
            Log.e(TAG, "Key event failed: ${e.message}")
        }
    }

    fun disconnect() {
        try {
            socket?.close()
        } catch (e: Exception) { }
        socket = null
        input = null
        output = null
        _connected.value = false
        Log.i(TAG, "VNC disconnected")
    }
}
