package com.ryzix.vm.model

import java.util.UUID

enum class VMArch(val displayName: String) {
    X86_64("x86_64"),
    AARCH64("ARM64 (aarch64)"),
    I386("x86 (32-bit)")
}

enum class VMStatus {
    STOPPED, STARTING, RUNNING, STOPPING, ERROR
}

data class VMConfig(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "New VM",
    val arch: VMArch = VMArch.X86_64,
    val ramMB: Int = 512,
    val cpuCores: Int = 2,
    val diskImagePath: String = "",
    val cdromImagePath: String = "",
    val vncPort: Int = 5900,
    val bootFromCdrom: Boolean = true,
    val enableKvm: Boolean = false,
    val extraArgs: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Build the QEMU argv array for this VM config.
 *
 * [biosDir] – path to directory containing bios-256k.bin etc.
 *             Pass an empty string if unknown (QEMU will try built-in path).
 *
 * KEY DECISIONS:
 * - argv[0] is ALWAYS "qemu-system-x86_64" regardless of arch because we
 *   only ship libqemu-system-x86_64.so (Limbo x86 APK).  Passing the wrong
 *   binary name would cause qemu_init to assert/crash immediately.
 * - AARCH64 / I386 VMs are accepted in the UI but run inside the x86_64
 *   engine; the machine/cpu flags are adjusted accordingly.
 * - Networking is disabled (-net none) to avoid driver init crashes on
 *   devices that restrict raw socket access.
 */
fun VMConfig.toQEMUArgs(biosDir: String = ""): Array<String> {
    val args = mutableListOf<String>()

    // argv[0] – must always match the loaded library (x86_64 only for now)
    args.add("qemu-system-x86_64")

    // BIOS / ROM search path
    if (biosDir.isNotEmpty()) {
        args.add("-L")
        args.add(biosDir)
    }

    args.add("-m");  args.add("${ramMB}M")
    args.add("-smp"); args.add("$cpuCores")

    // No graphical output; VNC for display
    args.add("-display"); args.add("none")
    args.add("-vnc");     args.add(":${vncPort - 5900}")

    args.add("-rtc");    args.add("base=utc")
    args.add("-no-reboot")

    // Skip /etc/qemu/*.conf and ~/.config/qemu/*.conf — those paths don't
    // exist on Android; Limbo's android_fopen crashes on null-resolved paths.
    args.add("-nodefconfig")
    args.add("-no-user-config")

    // Machine & CPU
    when (arch) {
        VMArch.X86_64, VMArch.I386 -> {
            args.add("-machine"); args.add("pc")
            args.add("-cpu");     args.add("qemu64")
        }
        VMArch.AARCH64 -> {
            // Still using x86_64 engine — best we can do without aarch64 lib
            args.add("-machine"); args.add("pc")
            args.add("-cpu");     args.add("qemu64")
        }
    }

    // Disk image
    if (diskImagePath.isNotEmpty()) {
        args.add("-drive")
        args.add("file=$diskImagePath,format=qcow2,if=virtio")
    }

    // CDROM / ISO
    if (cdromImagePath.isNotEmpty()) {
        args.add("-drive")
        args.add("file=$cdromImagePath,format=raw,if=virtio,media=cdrom")
        if (bootFromCdrom) { args.add("-boot"); args.add("d") }
    }

    if (enableKvm) { args.add("-enable-kvm") }

    // Disable networking to avoid socket/driver crashes on restricted devices
    args.add("-net"); args.add("none")

    if (extraArgs.isNotEmpty()) {
        args.addAll(extraArgs.split(" ").filter { it.isNotBlank() })
    }

    return args.toTypedArray()
}
