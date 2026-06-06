package com.ryzix.vm.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ryzix.vm.model.VMArch
import com.ryzix.vm.model.VMConfig
import com.ryzix.vm.model.VMStatus
import com.ryzix.vm.model.toQEMUArgs
import com.ryzix.vm.qemu.QEMUBridge
import com.ryzix.vm.qemu.QEMUService
import com.ryzix.vm.vnc.VNCClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class VMViewModel(application: Application) : AndroidViewModel(application) {

    private val TAG = "VMViewModel"
    private val PREFS_NAME = "ryzixvm_prefs"
    private val PREFS_KEY_VMS = "vm_list"

    private val _vmList = MutableStateFlow<List<VMConfig>>(emptyList())
    val vmList: StateFlow<List<VMConfig>> = _vmList

    private val _activeVM = MutableStateFlow<VMConfig?>(null)
    val activeVM: StateFlow<VMConfig?> = _activeVM

    private val _vmStatus = MutableStateFlow(VMStatus.STOPPED)
    val vmStatus: StateFlow<VMStatus> = _vmStatus

    private val _statusMessage = MutableStateFlow("")
    val statusMessage: StateFlow<String> = _statusMessage

    private val _qemuVersion = MutableStateFlow("")
    val qemuVersion: StateFlow<String> = _qemuVersion

    private val _downloadProgress = MutableStateFlow<Float?>(null)
    val downloadProgress: StateFlow<Float?> = _downloadProgress

    private val _downloadMessage = MutableStateFlow("")
    val downloadMessage: StateFlow<String> = _downloadMessage

    val vncClient = VNCClient()
    private var vncPollingJob: Job? = null
    private var downloadJob: Job? = null

    init {
        loadVMList()
        _qemuVersion.value = try {
            val nativeLibDir = getApplication<Application>().applicationInfo.nativeLibraryDir
            QEMUBridge.getVersion(nativeLibDir)
        } catch (e: Exception) {
            "Library not loaded"
        }
        if (_vmList.value.isEmpty()) {
            addDefaultVMs()
        }
        // Copy BIOS/ROM files from assets to internal storage on first run
        viewModelScope.launch(Dispatchers.IO) {
            copyBiosAssets(getApplication())
        }
    }

    // ── Persistence ──────────────────────────────────────────────────────────

    private fun loadVMList() {
        val prefs = getApplication<Application>()
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(PREFS_KEY_VMS, null) ?: return
        try {
            val arr = JSONArray(json)
            val list = mutableListOf<VMConfig>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(
                    VMConfig(
                        id             = o.optString("id",   java.util.UUID.randomUUID().toString()),
                        name           = o.optString("name", "VM"),
                        arch           = runCatching { VMArch.valueOf(o.optString("arch")) }
                                             .getOrDefault(VMArch.X86_64),
                        ramMB          = o.optInt("ramMB",     512),
                        cpuCores       = o.optInt("cpuCores",    2),
                        diskImagePath  = o.optString("diskImagePath",  ""),
                        cdromImagePath = o.optString("cdromImagePath", ""),
                        vncPort        = o.optInt("vncPort",  5900),
                        bootFromCdrom  = o.optBoolean("bootFromCdrom", true),
                        enableKvm      = o.optBoolean("enableKvm",    false),
                        extraArgs      = o.optString("extraArgs",       ""),
                        createdAt      = o.optLong("createdAt",  System.currentTimeMillis())
                    )
                )
            }
            _vmList.value = list
            Log.i(TAG, "Loaded ${list.size} VMs from prefs")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load VM list: ${e.message}")
        }
    }

    private fun saveVMList() {
        try {
            val arr = JSONArray()
            _vmList.value.forEach { vm ->
                arr.put(JSONObject().apply {
                    put("id",             vm.id)
                    put("name",           vm.name)
                    put("arch",           vm.arch.name)
                    put("ramMB",          vm.ramMB)
                    put("cpuCores",       vm.cpuCores)
                    put("diskImagePath",  vm.diskImagePath)
                    put("cdromImagePath", vm.cdromImagePath)
                    put("vncPort",        vm.vncPort)
                    put("bootFromCdrom",  vm.bootFromCdrom)
                    put("enableKvm",      vm.enableKvm)
                    put("extraArgs",      vm.extraArgs)
                    put("createdAt",      vm.createdAt)
                })
            }
            getApplication<Application>()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putString(PREFS_KEY_VMS, arr.toString()).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save VM list: ${e.message}")
        }
    }

    // ── VM management ─────────────────────────────────────────────────────────

    private fun addDefaultVMs() {
        _vmList.value = listOf(
            VMConfig(
                name = "Alpine Linux (Test)",
                arch = VMArch.X86_64,
                ramMB = 256,
                cpuCores = 1,
                cdromImagePath = "",
                bootFromCdrom = true
            )
        )
    }

    fun addVM(config: VMConfig) {
        _vmList.value = _vmList.value + config
        saveVMList()
    }

    fun removeVM(id: String) {
        _vmList.value = _vmList.value.filter { it.id != id }
        saveVMList()
    }

    // ── VM lifecycle ──────────────────────────────────────────────────────────

    fun startVM(config: VMConfig) {
        viewModelScope.launch(Dispatchers.IO) {
            _vmStatus.value = VMStatus.STARTING
            _activeVM.value = config
            _statusMessage.value = "Starting ${config.name}…"

            try {
                val app = getApplication<Application>()
                val biosDir = File(app.filesDir, "qemu_bios").absolutePath
                val args = config.toQEMUArgs(biosDir)
                Log.i(TAG, "QEMU args: ${args.joinToString(" ")}")

                val svcIntent = Intent(app, QEMUService::class.java).apply {
                    putExtra("vm_name", config.name)
                }
                app.startForegroundService(svcIntent)

                val result = QEMUBridge.startQEMU(app, args)
                if (result == 0) {
                    _vmStatus.value = VMStatus.RUNNING
                    _statusMessage.value = "${config.name} is running"
                    delay(2000)
                    connectVNC(config)
                } else {
                    _vmStatus.value = VMStatus.ERROR
                    _statusMessage.value = "QEMU failed (code $result)"
                }
            } catch (e: Exception) {
                Log.e(TAG, "startVM error: ${e.message}")
                _vmStatus.value = VMStatus.ERROR
                _statusMessage.value = "Error: ${e.message}"
            }
        }
    }

    fun stopVM() {
        viewModelScope.launch(Dispatchers.IO) {
            _vmStatus.value = VMStatus.STOPPING
            _statusMessage.value = "Stopping VM…"
            vncPollingJob?.cancel()
            vncClient.disconnect()
            QEMUBridge.stopQEMU()
            getApplication<Application>().stopService(
                Intent(getApplication(), QEMUService::class.java)
            )
            delay(500)
            _vmStatus.value = VMStatus.STOPPED
            _statusMessage.value = "VM stopped"
            _activeVM.value = null
        }
    }

    // ── BIOS asset copy ───────────────────────────────────────────────────────

    /**
     * Copies QEMU BIOS files from app assets (bundled at build time from the
     * Limbo APK) to internal storage so QEMU can load them via -L flag.
     */
    private fun copyBiosAssets(context: Context) {
        val biosDir = File(context.filesDir, "qemu_bios")
        biosDir.mkdirs()
        try {
            val files = context.assets.list("qemu_bios") ?: emptyArray()
            if (files.isEmpty()) {
                Log.w(TAG, "No BIOS assets found in assets/qemu_bios/")
                return
            }
            for (file in files) {
                val dest = File(biosDir, file)
                if (dest.exists()) continue
                context.assets.open("qemu_bios/$file").use { i ->
                    FileOutputStream(dest).use { o -> i.copyTo(o) }
                }
            }
            Log.i(TAG, "BIOS assets ready: ${files.size} files in ${biosDir.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "copyBiosAssets failed: ${e.message}")
        }
    }

    // ── Storage helpers ───────────────────────────────────────────────────────

    fun getRyzixVMDir(context: Context): File {
        val primary = File(Environment.getExternalStorageDirectory(), "RyzixVM")
        return try {
            if (primary.exists() || primary.mkdirs()) {
                val probe = File(primary, ".probe")
                probe.createNewFile(); probe.delete()
                primary
            } else fallbackDir(context)
        } catch (_: Exception) { fallbackDir(context) }
    }

    private fun fallbackDir(context: Context): File =
        File(context.getExternalFilesDir(null), "RyzixVM").also { it.mkdirs() }

    // ── Download & auto-setup ─────────────────────────────────────────────────

    fun downloadAndSetup(context: Context) {
        if (_downloadProgress.value != null) return
        downloadJob?.cancel()
        downloadJob = viewModelScope.launch(Dispatchers.IO) {
            _downloadProgress.value = 0f
            _downloadMessage.value = "Connecting…"

            val dir     = getRyzixVMDir(context)
            val isoFile = File(dir, "alpine-virt-3.19-x86_64.iso")

            try {
                val conn = URL(
                    "https://dl-cdn.alpinelinux.org/alpine/v3.19/releases/x86_64/" +
                    "alpine-virt-3.19.3-x86_64.iso"
                ).openConnection() as HttpURLConnection
                conn.connectTimeout = 20_000
                conn.readTimeout    = 60_000
                conn.setRequestProperty("User-Agent", "RyzixVM/1.0")
                conn.connect()

                if (conn.responseCode !in 200..299) {
                    _downloadProgress.value = null
                    _downloadMessage.value = "Server error ${conn.responseCode}"
                    return@launch
                }

                val total = conn.contentLengthLong
                _downloadMessage.value = "Downloading Alpine Linux virt (~60 MB)…"

                var downloaded = 0L
                conn.inputStream.use { input ->
                    FileOutputStream(isoFile).use { output ->
                        val buf = ByteArray(16_384)
                        var n = input.read(buf)
                        while (n >= 0 && isActive) {
                            output.write(buf, 0, n)
                            downloaded += n
                            if (total > 0) _downloadProgress.value = downloaded.toFloat() / total
                            n = input.read(buf)
                        }
                    }
                }

                if (!isActive) {
                    isoFile.delete()
                    _downloadProgress.value = null
                    _downloadMessage.value = "Download cancelled"
                    return@launch
                }

                val vm = VMConfig(
                    name           = "Alpine Linux 3.19",
                    arch           = VMArch.X86_64,
                    ramMB          = 256,
                    cpuCores       = 1,
                    cdromImagePath = isoFile.absolutePath,
                    diskImagePath  = "",
                    bootFromCdrom  = true,
                    vncPort        = 5900
                )
                _vmList.value = _vmList.value.filter { it.name != vm.name } + vm
                saveVMList()

                _downloadProgress.value = null
                _downloadMessage.value  =
                    "Done! \"Alpine Linux 3.19\" VM created.\n" +
                    "ISO: ${isoFile.absolutePath}\nGo to Home and tap Start."

            } catch (e: Exception) {
                isoFile.delete()
                _downloadProgress.value = null
                _downloadMessage.value  = "Failed: ${e.message}"
                Log.e(TAG, "Download error", e)
            }
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        _downloadProgress.value = null
        _downloadMessage.value  = "Download cancelled"
    }

    // ── VNC ───────────────────────────────────────────────────────────────────

    private suspend fun connectVNC(config: VMConfig) {
        _statusMessage.value = "Connecting to display…"
        repeat(10) {
            if (vncClient.connect("127.0.0.1", config.vncPort)) {
                _statusMessage.value = "Display connected"
                startVNCPolling()
                return
            }
            delay(1000)
        }
        _statusMessage.value = "Display unavailable — VM may still be running"
    }

    private fun startVNCPolling() {
        vncPollingJob?.cancel()
        vncPollingJob = viewModelScope.launch(Dispatchers.IO) {
            while (_vmStatus.value == VMStatus.RUNNING) {
                try {
                    vncClient.requestFramebufferUpdate(incremental = true)
                    delay(33)
                } catch (e: Exception) {
                    Log.e(TAG, "VNC polling: ${e.message}")
                    break
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        vncPollingJob?.cancel()
        downloadJob?.cancel()
        vncClient.disconnect()
    }
}
