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
            Log.i(TAG, "Native bridge loaded successfully")
            // DO NOT call getVersion() here — it used to trigger dlopen of the
            // QEMU library at startup, which runs QEMU's global C++ constructors
            // and causes an immediate SIGSEGV on MIUI / strict-SELinux devices.
            // getVersion() now only stat()s the file and is safe to call later.
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

    /**
     * Returns the QEMU version string.
     * Safe to call at any time — uses stat() only, never dlopen.
     * [nativeLibDir] = context.applicationInfo.nativeLibraryDir
     */
    fun getVersion(nativeLibDir: String): String = try {
        getVersion_native(nativeLibDir)
    } catch (e: UnsatisfiedLinkError) {
        "Native bridge not loaded"
    }

    private external fun nativeStartQEMU(libPath: String, args: Array<String>): Int

    external fun stopQEMU()
    external fun isRunning(): Boolean

    @Suppress("FunctionName")
    private external fun getVersion_native(nativeLibDir: String): String
}
