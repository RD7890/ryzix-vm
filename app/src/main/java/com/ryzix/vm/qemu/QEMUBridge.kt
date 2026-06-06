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
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load native bridge: ${e.message}")
        }
    }

    /**
     * Start QEMU.
     * [biosDir]     = directory containing bios-256k.bin and other ROM files
     * [storageDir]  = writable app directory (passed to Limbo's set_jni as
     *                 storage_base_dir so android_fopen doesn't crash on NULL)
     */
    fun startQEMU(context: Context, args: Array<String>): Int {
        val nativeLibDir = context.applicationInfo.nativeLibraryDir
        val qemuLibPath  = "$nativeLibDir/$QEMU_LIB_NAME"
        val biosDir      = File(context.filesDir, "qemu_bios").absolutePath
        val storageDir   = context.filesDir.absolutePath
        Log.i(TAG, "QEMU lib: $qemuLibPath (exists=${File(qemuLibPath).exists()})")
        Log.i(TAG, "biosDir=$biosDir  storageDir=$storageDir")
        return nativeStartQEMU(qemuLibPath, biosDir, storageDir, args)
    }

    fun getVersion(nativeLibDir: String): String = try {
        getVersion_native(nativeLibDir)
    } catch (e: UnsatisfiedLinkError) {
        "Native bridge not loaded"
    }

    private external fun nativeStartQEMU(
        libPath: String,
        biosDir: String,
        storageDir: String,
        args: Array<String>
    ): Int

    external fun stopQEMU()
    external fun isRunning(): Boolean

    @Suppress("FunctionName")
    private external fun getVersion_native(nativeLibDir: String): String
}
