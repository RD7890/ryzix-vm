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

    // null = idle, 0.0..1.0 = downloading in progress
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
        // Only add defaults if nothing was saved previously
        if (_vmList.value.isEmpty()) {
            addDefaultVMs()
        }
    }

    // ─── Persistence (SharedPreferences + org.json) ───────────────────────────

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
                        id            = o.optString("id",   java.util.UUID.randomUUID().toString()),
                        name          = o.optString("name", "VM"),
                        arch          = runCatching { VMArch.valueOf(o.optString("arch")) }
                                            .getOrDefault(VMArch.X86_64),
                        ramMB         = o.optInt("ramMB",     512),
                        cpuCores      = o.optInt("cpuCores",    2),
                        diskImagePath = o.optString("diskImagePath",  ""),
                        cdromImagePath= o.optString("cdromImagePath", ""),
                        vncPort       = o.optInt("vncPort",  5900),
                        bootFromCdrom = o.optBoolean("bootFromCdrom", true),
                        enableKvm     = o.optBoolean("enableKvm",    false),
                        extraArgs     = o.optString("extraArgs",       ""),
                        createdAt     = o.optLong("createdAt",  System.currentTimeMillis())
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
                .edit()
                .putString(PREFS_KEY_VMS, arr.toString())
                .apply()
            Log.i(TAG, "Saved ${_vmList.value.size} VMs to prefs")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save VM list: ${e.message}")
        }
    }

    // ─── VM management ────────────────────────────────────────────────────────

    private fun addDefaultVMs() {
        _vmList.value = listOf(
            VMConfig(
                name = "Alpine Linux (Test)",
                arch = VMArch.X86_64,
                ramMB = 512,
                cpuCores = 1,
                cdromImagePath = "",
                bootFromCdrom = true
            )
        )
        // Don't save defaults — let the user configure real paths first
    }

    fun addVM(config: VMConfig) {
        _vmList.value = _vmList.value + config
        saveVMList()
    }

    fun removeVM(id: String) {
        _vmList.value = _vmList.value.filter { it.id != id }
        saveVMList()
    }

    // ─── VM lifecycle ─────────────────────────────────────────────────────────

    fun startVM(config: VMConfig) {
        viewModelScope.launch(Dispatchers.IO) {
            _vmStatus.value = VMStatus.STARTING
            _activeVM.value = config
            _statusMessage.value = "Starting ${config.name}…"

            try {
                val args = config.toQEMUArgs()
                Log.i(TAG, "QEMU args: ${args.joinToString(" ")}")

                val svcIntent = Intent(getApplication(), QEMUService::class.java).apply {
                    putExtra("vm_name", config.name)
                }
                getApplication<Application>().startForegroundService(svcIntent)

                val result = QEMUBridge.startQEMU(getApplication(), args)
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

    // ─── Storage helpers ──────────────────────────────────────────────────────

    /**
     * Returns the RyzixVM directory on external storage.
     * Primary: /storage/emulated/0/RyzixVM/ (visible in any file manager).
     * Fallback: app-scoped external dir (always writable, still visible on most
     * Android versions under Android/data/com.ryzix.vm/files/RyzixVM/).
     */
    fun getRyzixVMDir(context: Context): File {
        val primary = File(Environment.getExternalStorageDirectory(), "RyzixVM")
        return try {
            if (primary.exists() || primary.mkdirs()) {
                val probe = File(primary, ".probe")
                probe.createNewFile()
                probe.delete()
                primary
            } else {
                fallbackDir(context)
            }
        } catch (_: Exception) {
            fallbackDir(context)
        }
    }

    private fun fallbackDir(context: Context): File =
        File(context.getExternalFilesDir(null), "RyzixVM").also { it.mkdirs() }

    // ─── Download & auto-setup ────────────────────────────────────────────────

    /**
     * Downloads Alpine Linux virt ISO (~60 MB), shows live % progress,
     * then automatically creates a ready-to-boot VM config so the user
     * only needs to tap Start.
     */
    fun downloadAndSetup(context: Context) {
        if (_downloadProgress.value != null) return
        downloadJob?.cancel()
        downloadJob = viewModelScope.launch(Dispatchers.IO) {
            _downloadProgress.value = 0f
            _downloadMessage.value = "Connecting…"

            val dir      = getRyzixVMDir(context)
            val isoFile  = File(dir, "alpine-virt-3.19-x86_64.iso")

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
                    _downloadMessage.value = "Server error ${conn.responseCode} — check internet"
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
                            if (total > 0) {
                                _downloadProgress.value = downloaded.toFloat() / total.toFloat()
                            }
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

                // ── Auto-create a ready-to-boot VM ────────────────────────
                val vm = VMConfig(
                    name           = "Alpine Linux 3.19",
                    arch           = VMArch.X86_64,
                    ramMB          = 512,
                    cpuCores       = 1,
                    cdromImagePath = isoFile.absolutePath,
                    diskImagePath  = "",
                    bootFromCdrom  = true,
                    vncPort        = 5900
                )
                // Remove any existing Alpine entry before adding the fresh one
                _vmList.value = _vmList.value
                    .filter { it.name != vm.name } + vm
                saveVMList()

                _downloadProgress.value = null
                _downloadMessage.value  =
                    "Done! \"Alpine Linux 3.19\" VM created.\n" +
                    "ISO: ${isoFile.absolutePath}\n" +
                    "Go to Home and tap Start."
                Log.i(TAG, "ISO saved + VM created: ${isoFile.absolutePath}")

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
        downloadJob = null
        _downloadProgress.value = null
        _downloadMessage.value  = "Download cancelled"
    }

    // ─── VNC ──────────────────────────────────────────────────────────────────

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
        _statusMessage.value = "Display connection failed — VM may still be running"
    }

    private fun startVNCPolling() {
        vncPollingJob?.cancel()
        vncPollingJob = viewModelScope.launch(Dispatchers.IO) {
            while (_vmStatus.value == VMStatus.RUNNING) {
                try {
                    vncClient.requestFramebufferUpdate(incremental = true)
                    delay(33)
                } catch (e: Exception) {
                    Log.e(TAG, "VNC polling error: ${e.message}")
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
