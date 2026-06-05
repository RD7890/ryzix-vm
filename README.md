# Ryzix VM

Open-source Android virtual machine app powered by QEMU. Run full Linux OS images on your Android phone.

## Features

- QEMU-powered ARM64 virtual machine
- Jetpack Compose UI with dark theme
- Built-in VNC display (touch = mouse click)
- Pinch-to-zoom display
- Virtual keyboard input
- Foreground service (VM runs in background)
- Ctrl+Alt+Del button

## Requirements

- Android 8.0+ (API 26)
- ARM64 device (64-bit)
- 4GB RAM recommended
- 6GB+ free storage

## Building

### Via GitHub Actions (Recommended)

1. Fork this repo
2. Push to `main` or `dev` branch
3. GitHub Actions builds APK automatically
4. Download APK from Releases or Actions artifacts

### Manually

```bash
# Requires: Android NDK r27c, JDK 17
./gradlew :app:assembleRelease
```

## First Time Setup

1. Install APK
2. Download a Linux image:
   - **Test (100MB):** Tiny Core Linux ARM64
   - **Full:** Debian 12 ARM64 (~700MB)
3. Create VM → set image path
4. Start VM → display auto-connects via VNC

## Image Downloads

| OS | Size | Antigravity | Link |
|---|---|---|---|
| Tiny Core Linux | ~100MB | ❌ (test only) | tinycorelinux.net |
| Alpine + XFCE | ~500MB | ❌ (musl libc) | alpinelinux.org |
| Debian 12 XFCE | ~700MB | ✅ | debian.org/CD/netinst |

## License

- App code: Apache 2.0
- QEMU: GPL-2.0
