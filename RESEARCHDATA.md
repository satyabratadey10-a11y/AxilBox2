# RESEARCHDATA.md — Deep Technical Reference Data

This document provides exhaustive, production-grade technical reference data for AxilBox engineering. Contributors and AI agents must consult this reference before designing or modifying low-level native, kernel, virtualization, or streaming subsystems in Phases 2 and 3.

---

## 1. Linux Kernel Configuration for ARM64 `virt` Target

When building the custom guest Linux/AOSP kernel for the QEMU `virt` machine (`qemu-system-aarch64 -M virt,gic-version=3`), the kernel configuration (`.config`) MUST explicitly include the following drivers and flags. Standard defconfigs for physical SoCs (like Qualcomm or Exynos) will fail to mount or initialize console output.

```ini
# Core Architecture & Addressing
CONFIG_ARM64=y
CONFIG_ARM64_VA_BITS_48=y
CONFIG_ARM64_PA_BITS_48=y
CONFIG_ARM64_4K_PAGES=y
CONFIG_ARM64_GIC_V3=y
CONFIG_ARM64_GIC_V3_ITS=y
CONFIG_PCI=y
CONFIG_PCI_HOST_GENERIC=y
CONFIG_PCI_ECAM=y

# Serial & Early Boot Telemetry Console
CONFIG_SERIAL_AMBA_PL011=y
CONFIG_SERIAL_AMBA_PL011_CONSOLE=y
CONFIG_SERIAL_EARLYCON=y
CONFIG_SERIAL_EARLYCON_ARM_SEMIHOST=n

# VirtIO Core & Transport
CONFIG_VIRTIO=y
CONFIG_VIRTIO_PCI=y
CONFIG_VIRTIO_PCI_LEGACY=n
CONFIG_VIRTIO_PCI_MODERN=y
CONFIG_VIRTIO_MMIO=y
CONFIG_VIRTIO_MMIO_CMDLINE_DEVICES=y

# VirtIO Storage (Guest Disk & Swap)
CONFIG_VIRTIO_BLK=y
CONFIG_BLK_DEV_SD=y
CONFIG_EXT4_FS=y
CONFIG_EXT4_FS_POSIX_ACL=y
CONFIG_EXT4_FS_SECURITY=y
CONFIG_F2FS_FS=y
CONFIG_OVERLAY_FS=y

# VirtIO Display & Graphics Subsystem
CONFIG_DRM=y
CONFIG_DRM_VIRTIO_GPU=y
CONFIG_DRM_VIRTIO_GPU_KMS=y
CONFIG_FB=y
CONFIG_FB_SIMPLE=y
CONFIG_FRAMEBUFFER_CONSOLE=y

# VirtIO Input & Human Interface
CONFIG_INPUT=y
CONFIG_INPUT_EVDEV=y
CONFIG_INPUT_TOUCHSCREEN=y
CONFIG_INPUT_KEYBOARD=y
CONFIG_VIRTIO_INPUT=y

# VirtIO Networking & Entropy
CONFIG_NETDEVICES=y
CONFIG_NET_CORE=y
CONFIG_VIRTIO_NET=y
CONFIG_HW_RANDOM=y
CONFIG_HW_RANDOM_VIRTIO=y

# Android Binder & Ashmem Subsystems (for AOSP Guest)
CONFIG_ANDROID=y
CONFIG_ANDROID_BINDER_IPC=y
CONFIG_ANDROID_BINDERFS=y
CONFIG_ANDROID_BINDER_DEVICES="binder,hwbinder,vndbinder"
CONFIG_ASHMEM=y
CONFIG_MEMFD_CREATE=y
CONFIG_SYNC_FILE=y
CONFIG_DMA_SHARED_BUFFER=y
```

---

## 2. Low-Latency WebRTC & Hardware MediaCodec Video Pipeline

Phase 3 implements cloud VM display streaming via Google's native `libwebrtc` C++ AAR without WebViews. The pipeline parameters below guarantee sub-50ms glass-to-glass latency over Wi-Fi and 5G networks.

```
Cloud VM (QEMU/KVM)                   Host Android Device (AxilBox)
┌─────────────────────────┐           ┌─────────────────────────────┐
│  VM Display Buffer      │           │  Native WebRTC PeerConn     │
│  (1080x1920 / 720x1280) │           │  (google-webrtc AAR)        │
└────────────┬────────────┘           └──────────────┬──────────────┘
             │ DMA / EGL Image                       │ RTP / SRTP H.264
             ▼                                       ▼
┌─────────────────────────┐           ┌─────────────────────────────┐
│  NVENC / VAAPI Encoder  │           │  RTP Jitter Buffer (0-20ms) │
│  - Profile: Baseline    │           │  & Depacketizer             │
│  - GOP: 1s (intra-ref)  │           └──────────────┬──────────────┘
│  - B-frames: 0          │                          │ In-band SPS/PPS
│  - Rate: CBR 4.0 Mbps   │                          ▼
└────────────┬────────────┘           ┌─────────────────────────────┐
             │ Annex B ByteStream     │  MediaCodec Decoder         │
             ▼                        │  (Surface Mode / LowLatency)│
┌─────────────────────────┐           └──────────────┬──────────────┘
│  WebRTC VideoTrack      │                          │ Direct GPU Compositing
│  (libwebrtc Server)     │                          ▼
└────────────┬────────────┘           ┌─────────────────────────────┐
             │                        │  SurfaceViewRenderer        │
             └───────────────────────►│  (EGL / Vulkan Zero Copy)   │
                Direct P2P UDP/SRTP   └─────────────────────────────┘
```

### 2.1. Encoder Configuration (Server Side)
- **Codec:** H.264 (AVC) Baseline Profile (Level 4.1). Baseline profile eliminates B-frame reordering latency.
- **Keyframe Strategy:** Periodic IDR keyframes every 60 frames (1 second at 60fps), supplemented by slice-based intra-refresh to smooth packet transmission bursts.
- **Bitrate & Congestion Control:** Constant Bitrate (CBR) between 2,500 kbps (720p) and 6,000 kbps (1080p), managed via Google Congestion Control (GCC) with Transport-Wide Congestion Control (TWCC) feedback.
- **Color Format:** `NV12` / `YUV420p` matched to hardware encoder input surface.

### 2.2. Android Client Hardware MediaCodec Configuration
- **MediaFormat Keys:**
  ```kotlin
  val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
      setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
      setInteger(MediaFormat.KEY_BIT_RATE, 4_000_000)
      setInteger(MediaFormat.KEY_FRAME_RATE, 60)
      setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
      // Enable ultra-low latency decoding mode on Android 11+ (API 30+)
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
          setInteger(MediaFormat.KEY_LOW_LATENCY, 1)
      }
      setInteger(MediaFormat.KEY_PRIORITY, 0) // Realtime priority
  }
  ```
- **Output Target:** Decoded directly to `Surface` provided by `SurfaceViewRenderer` — zero userspace CPU memory copies.

---

## 3. Host Memory Budget & Allocation Thresholds

For a typical mid-tier Android host device with 6.0 GB of physical RAM, the operating system and background processes consume approximately 2.5–3.0 GB. AxilBox enforces the following memory ceiling rules:

```
Total Physical RAM: 6144 MB (6.0 GB)
├─ Android Host OS & System Services:    ~2560 MB (41.6%)
├─ Host Foreground Apps & Launcher:       ~512 MB  (8.3%)
├─ AxilBox Host Process & JNI Buffers:    ~256 MB  (4.2%)
└─ MAXIMUM Safe Guest Virtual RAM:        2816 MB (45.9%)
```

### Allocation Rules:
- **Maximum Recommended Allocation:** `2048 MB` (2.0 GB) — provides smooth guest OS execution without triggering host Android LMK (Low Memory Killer).
- **Hard Ceiling Allocation:** `3072 MB` (3.0 GB) — requires prominent UI warning indicating background host apps may be reclaimed.
- **Minimum Guest RAM:** `512 MB` (Linux terminal/busybox), `1536 MB` (AOSP minimal GUI).

---

## 4. The ARMv8 HWCAP / FlagM Hazard & Runtime Signal Probe

### 4.1. The Engineering Hazard
On heterogeneous ARM architectures (big.LITTLE / DynamIQ), big cores (e.g., Cortex-X1/A78) may support ARMv8.4-A Flag Manipulation (FlagM: `CFINV`, `RMIF`, `SETF8`), while LITTLE efficiency cores (e.g., Cortex-A55) in the same SoC may only support ARMv8.2-A.
Certain Android kernels incorrectly populate `getauxval(AT_HWCAP)` with `HWCAP_FLAGM` based on the boot core's capabilities. If a thread is scheduled or migrated to an efficiency core, executing `cfinv` triggers an unhandled `SIGILL` fault.

### 4.2. Safe Verification Pattern
Dynamic translation engines and JIT modules in AxilBox must NEVER rely solely on `getauxval(AT_HWCAP) & HWCAP_FLAGM`. Instead, execute a signal-guarded assembly probe during JNI library initialization:

```c
#include <signal.h>
#include <setjmp.h>
#include <stdbool.h>
#include <sys/auxv.h>

static sigjmp_buf g_probe_jmpbuf;

static void sigill_handler(int sig) {
    siglongjmp(g_probe_jmpbuf, 1);
}

bool probe_arm64_flagm_safe(void) {
    unsigned long hwcap = getauxval(AT_HWCAP);
    #ifndef HWCAP_FLAGM
    #define HWCAP_FLAGM (1UL << 20)
    #endif

    if (!(hwcap & HWCAP_FLAGM)) {
        return false; // Not advertised by kernel
    }

    struct sigaction sa, old_sa;
    sa.sa_handler = sigill_handler;
    sigemptyset(&sa.sa_mask);
    sa.sa_flags = 0;
    sigaction(SIGILL, &sa, &old_sa);

    bool flagm_supported = false;
    if (sigsetjmp(g_probe_jmpbuf, 1) == 0) {
        // Guarded execution of FlagM instruction: CFINV (Invert Condition Flags)
        __asm__ __volatile__(".inst 0xd500401f" ::: "cc"); // cfinv
        flagm_supported = true;
    } else {
        flagm_supported = false; // SIGILL caught! Core does not support FlagM
    }

    sigaction(SIGILL, &old_sa, NULL);
    return flagm_supported;
}
```

### 4.3. Architectural Layering Mandate
All JIT/translator code in AxilBox must strictly isolate three capability models:
1. **Host Physical Capability:** What the physical execution core actually supports (verified by signal probe).
2. **Translator Codegen Model:** The instruction set targeted by the dynamic binary translator.
3. **Guest Advertised Features:** The virtual CPUID/HWCAP flags exposed to the guest OS.

---

## 5. AOSP Source Compilation for the `virt` Target

A generic GSI (e.g. `system.img` from an OTA) assumes physical vendor HALs (`android.hardware.graphics.allocator`, vendor display HALs). To build a working AOSP image for QEMU `virt`:

1. **Initialize AOSP Manifest:**
   ```bash
   repo init -u https://android.googlesource.com/platform/manifest -b android-14.0.0_r50
   ```
2. **Target Device Tree:** Build using the generic AOSP target with software GL rendering and virtio drivers:
   ```bash
   source build/envsetup.sh
   lunch gsi_arm64-userdebug
   ```
3. **Integrate MiniGBM / VirtIO Gralloc:** Configure `BoardConfig.mk` to enable DRM/KMS virtio gralloc:
   ```makefile
   BOARD_USES_MINIGBM := true
   BOARD_GPU_DRIVERS := virtio-gpu
   BOARD_KERNEL_CMDLINE += console=ttyAMA0 earlycon=pl011,0x09000000 androidboot.hardware=virt
   ```
4. **Generate Sparse Images:** Output `system.img`, `vendor.img`, and `ramdisk.img` are converted to raw ext4 images using `simg2img` before attaching as `virtio-blk` disks.

---

## 6. Android 10+ (API 29+) W^X Execution Restrictions & Native Library Packaging (`jniLibs`)

### 6.1. The Platform Constraint (W^X & `noexec` on Internal Storage)
Starting in Android 10 (API level 29), Google enforced W^X (Write XOR Execute) security restrictions across all app-private writable data directories (`/data/data/<package>/`, `/data/user/0/<package>/files/`, and `/data/user/0/<package>/cache/`).
Specifically, the underlying filesystems and SELinux policies prevent `execve()` execution of binaries placed in writable app directories. Attempting to execute an ELF binary extracted to `context.filesDir` (e.g. via `chmod +x` and `ProcessBuilder`/`execve`) fails unconditionally with `java.io.IOException: Cannot run program ...: error=13, Permission denied`, regardless of UNIX file permission bits (`0755` / `0777`).

### 6.2. The Native Library (`nativeLibraryDir`) Exemption
Android explicitly exempts the application's native library directory (`context.applicationInfo.nativeLibraryDir`, e.g. `/data/app/.../lib/arm64/`) from this restriction. Binaries and shared libraries extracted into `nativeLibraryDir` at APK installation time are mounted read-only and executable (`r-x`) under SELinux rules, allowing direct `execve()` invocation.

### 6.3. APK Packaging Requirements & Renaming Rule
Android Gradle Plugin (AGP) and the package installer enforce strict naming requirements for files placed in `app/src/main/jniLibs/<abi>/`:
1. **Naming Pattern:** Every file MUST start with `lib` and end with `.so` (e.g., `libqemu_system_aarch64.so`, `libglib-2.0.so`, `libpixman-1.so`). Files without the `lib` prefix or with trailing version numbers (e.g. `libglib-2.0.so.0` or raw `qemu-system-aarch64`) are silently skipped and omitted from the APK.
2. **Extraction Mandate (`extractNativeLibs="true"`):** `AndroidManifest.xml` must set `android:extractNativeLibs="true"`, and `app/build.gradle.kts` must set `packaging.jniLibs.useLegacyPackaging = true`. This prevents AGP from storing uncompressed `.so` files (page-aligned mmap mode introduced in Android 6.0/23), guaranteeing physical extraction to `nativeLibraryDir` on disk where a concrete file path is available for `execve()`.
3. **RPATH & Dependency Resolution:** All bundled `.so` files are co-located in `nativeLibraryDir`. Running `patchelf --set-rpath '$ORIGIN'` and updating `DT_NEEDED` / `DT_SONAME` ensures that when Bionic's dynamic linker executes `libqemu_system_aarch64.so`, it resolves all sibling dependencies (`libglib-2.0.so`, `libpixman-1.so`, `libandroid-shmem.so`, etc.) directly from `$ORIGIN` without requiring external paths.

### 6.4. Verification Boundaries: CI vs Physical Device
> [!IMPORTANT]
> **Host CI Runner vs Physical Device Verification:**
> - **Verifiable in CI:** Packaging script execution, ELF `lib*.so` name canonicalization, `patchelf --set-rpath '$ORIGIN'`, `DT_NEEDED` patching, Gradle APK assembly, APK `.so` contents inspection (`unzip -l app-debug.apk`), and host-native QEMU kernel sanity testing.
> - **Cannot Be Verified in CI:** On-device `execve()` execution of the Bionic ARM64 QEMU binary from `nativeLibraryDir` under Android 10+ SELinux and zygote process constraints. This path **requires manual testing on a physical ARM64 Android device** after each modification.
