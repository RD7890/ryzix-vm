package com.ryzix.vm.qemu

import android.content.Context
import android.util.Log
import java.io.File

object QEMUBridge {

    private const val TAG = "QEMUBridge"
    private const val QEMU_LIB_NAME = "libqemu-system-x86_64.so"

    init {
        try {
            System.loadLibrary("ryzixvm")
            Log.i(TAG, "Native bridge loaded: ${getVersion()}")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load native bridge: ${e.message}")
        }
    }

    fun startQEMU(context: Context, args: Array<String>): Int {
        val nativeLibDir = context.applicationInfo.nativeLibraryDir
        val qemuLibPath = "$nativeLibDir/$QEMU_LIB_NAME"
        Log.i(TAG, "QEMU lib: $qemuLibPath (exists=${File(qemuLibPath).exists()})")
        return nativeStartQEMU(qemuLibPath, args)
    }

    private external fun nativeStartQEMU(libPath: String, args: Array<String>): Int

    external fun stopQEMU()
    external fun isRunning(): Boolean
    external fun getVersion(): String
}
