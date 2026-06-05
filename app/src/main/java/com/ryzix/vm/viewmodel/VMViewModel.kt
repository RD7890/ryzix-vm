package com.ryzix.vm.viewmodel

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ryzix.vm.model.VMConfig
import com.ryzix.vm.model.VMArch
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
import kotlinx.coroutines.launch

class VMViewModel(application: Application) : AndroidViewModel(application) {

    private val TAG = "VMViewModel"

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

    val vncClient = VNCClient()

    private var vncPollingJob: Job? = null

    init {
        loadVMList()
        _qemuVersion.value = try {
            QEMUBridge.getVersion()
        } catch (e: Exception) {
            "Library not loaded"
        }
        addDefaultVMs()
    }

    private fun addDefaultVMs() {
        if (_vmList.value.isEmpty()) {
            _vmList.value = listOf(
                VMConfig(
                    name = "Tiny Core Linux (Test)",
                    arch = VMArch.X86_64,
                    ramMB = 128,
                    cpuCores = 1,
                    bootFromCdrom = true
                ),
                VMConfig(
                    name = "Debian 12 XFCE",
                    arch = VMArch.X86_64,
                    ramMB = 1024,
                    cpuCores = 2,
                    bootFromCdrom = false
                )
            )
        }
    }

    fun addVM(config: VMConfig) {
        _vmList.value = _vmList.value + config
        saveVMList()
    }

    fun removeVM(id: String) {
        _vmList.value = _vmList.value.filter { it.id != id }
        saveVMList()
    }

    fun startVM(config: VMConfig) {
        viewModelScope.launch(Dispatchers.IO) {
            _vmStatus.value = VMStatus.STARTING
            _activeVM.value = config
            _statusMessage.value = "Starting ${config.name}..."

            try {
                val args = config.toQEMUArgs()
                Log.i(TAG, "Starting QEMU with args: ${args.joinToString(" ")}")

                val serviceIntent = Intent(getApplication(), QEMUService::class.java).apply {
                    putExtra("vm_name", config.name)
                }
                getApplication<Application>().startForegroundService(serviceIntent)

                val result = QEMUBridge.startQEMU(getApplication(), args)

                if (result == 0) {
                    _vmStatus.value = VMStatus.RUNNING
                    _statusMessage.value = "${config.name} is running"

                    delay(2000)
                    connectVNC(config)
                } else {
                    _vmStatus.value = VMStatus.ERROR
                    _statusMessage.value = "Failed to start QEMU (code: $result)"
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start VM: ${e.message}")
                _vmStatus.value = VMStatus.ERROR
                _statusMessage.value = "Error: ${e.message}"
            }
        }
    }

    fun stopVM() {
        viewModelScope.launch(Dispatchers.IO) {
            _vmStatus.value = VMStatus.STOPPING
            _statusMessage.value = "Stopping VM..."

            vncPollingJob?.cancel()
            vncClient.disconnect()
            QEMUBridge.stopQEMU()

            val serviceIntent = Intent(getApplication(), QEMUService::class.java)
            getApplication<Application>().stopService(serviceIntent)

            delay(500)
            _vmStatus.value = VMStatus.STOPPED
            _statusMessage.value = "VM stopped"
            _activeVM.value = null
        }
    }

    private suspend fun connectVNC(config: VMConfig) {
        _statusMessage.value = "Connecting to display..."
        var retries = 0
        while (retries < 10) {
            val connected = vncClient.connect("127.0.0.1", config.vncPort)
            if (connected) {
                _statusMessage.value = "Display connected"
                startVNCPolling()
                return
            }
            retries++
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
                    delay(33) // ~30fps
                } catch (e: Exception) {
                    Log.e(TAG, "VNC polling error: ${e.message}")
                    break
                }
            }
        }
    }

    private fun loadVMList() {
        // In future: load from DataStore/Room
    }

    private fun saveVMList() {
        // In future: save to DataStore/Room
    }

    override fun onCleared() {
        super.onCleared()
        vncPollingJob?.cancel()
        vncClient.disconnect()
    }
}
