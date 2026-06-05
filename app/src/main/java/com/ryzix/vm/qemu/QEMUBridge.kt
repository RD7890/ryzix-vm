package com.ryzix.vm.qemu

import android.util.Log

object QEMUBridge {

    private const val TAG = "QEMUBridge"

    init {
        try {
            System.loadLibrary("ryzixvm")
            Log.i(TAG, "Native library loaded: ${getVersion()}")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load native library: ${e.message}")
        }
    }

    external fun startQEMU(args: Array<String>): Int
    external fun stopQEMU()
    external fun isRunning(): Boolean
    external fun getVersion(): String
}
