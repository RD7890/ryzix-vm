package com.ryzix.vm.model

import java.util.UUID

enum class VMArch(val displayName: String, val qemuBin: String) {
    AARCH64("ARM64 (aarch64)", "qemu-system-aarch64"),
    X86_64("x86_64", "qemu-system-x86_64"),
    I386("x86 (32-bit)", "qemu-system-i386")
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

fun VMConfig.toQEMUArgs(): Array<String> {
    val args = mutableListOf<String>()

    args.add(arch.qemuBin)
    args.add("-m")
    args.add("${ramMB}M")
    args.add("-smp")
    args.add("$cpuCores")

    // No graphical display backend — output goes through VNC only
    args.add("-display")
    args.add("none")

    args.add("-vnc")
    args.add(":${vncPort - 5900}")
    args.add("-rtc")
    args.add("base=utc")
    args.add("-no-reboot")

    // Prevent QEMU from reading /etc/qemu/*.conf and ~/.config/qemu/*.conf.
    // Those paths don't exist on Android; Limbo's android_fopen crashes with
    // SIGSEGV (null ptr in strcpy) when QEMU passes a null-resolved config
    // path to it. These flags skip qemu_read_config_file() entirely.
    args.add("-nodefconfig")
    args.add("-no-user-config")

    when (arch) {
        VMArch.AARCH64 -> {
            args.add("-machine")
            args.add("virt")
            args.add("-cpu")
            args.add("cortex-a72")
            args.add("-bios")
            args.add("QEMU_EFI.fd")
        }
        VMArch.X86_64 -> {
            args.add("-machine")
            args.add("pc")
            args.add("-cpu")
            args.add("qemu64")
        }
        VMArch.I386 -> {
            args.add("-machine")
            args.add("pc")
            args.add("-cpu")
            args.add("qemu32")
        }
    }

    if (diskImagePath.isNotEmpty()) {
        args.add("-drive")
        args.add("file=$diskImagePath,format=qcow2,if=virtio")
    }

    if (cdromImagePath.isNotEmpty()) {
        args.add("-drive")
        args.add("file=$cdromImagePath,format=raw,if=virtio,media=cdrom")
        if (bootFromCdrom) {
            args.add("-boot")
            args.add("d")
        }
    }

    if (enableKvm) {
        args.add("-enable-kvm")
    }

    args.add("-netdev")
    args.add("user,id=net0")
    args.add("-device")
    args.add("virtio-net-pci,netdev=net0")

    if (extraArgs.isNotEmpty()) {
        args.addAll(extraArgs.split(" ").filter { it.isNotBlank() })
    }

    return args.toTypedArray()
}
