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

### 1.1. Deterministic Build Caching Strategy for Guest Kernel (`actions/cache`)

Compiling an ARM64 Linux kernel Image from source via `kbuild` on GitHub Actions runners takes ~14–15 minutes from scratch. To eliminate redundant recompilations without risking stale or unversioned kernel binaries:

1. **Deterministic Key Formulation:**
   ```yaml
   key: kernel-virt-v6.6-${{ hashFiles('tools/kernel/configure-virt-kernel.sh') }}
   ```
   The cache key pairs the pinned upstream kernel release branch/tag (`v6.6 LTS`) with the cryptographic SHA-256 hash of [`tools/kernel/configure-virt-kernel.sh`](file:///data/data/com.termux/files/home/AxilBox2/tools/kernel/configure-virt-kernel.sh), which contains every `./scripts/config` directive.
2. **Guaranteed Invalidation:**
   Any modification to kernel configuration directives (e.g. enabling `CONFIG_VIRTIO_NET` or modifying console drivers) immediately changes the file hash and invalidates the cache key. A cache miss guarantees a fresh build matching the new configuration.
3. **Artifact Paths Cached:**
   The cache stores `linux-src/arch/arm64/boot/Image` and `linux-src/.config`.
4. **Cache-Hit Execution Flow:**
   On a cache hit (`steps.kernel-cache.outputs.cache-hit == 'true'`), Job 1 skips:
   - Toolchain & build dependencies installation (`apt-get install`)
   - 1.5GB Linux kernel shallow clone (`git clone --depth 1`)
   - `defconfig` / `scripts/config` / `olddefconfig` configuration steps
   - Multi-core kbuild compilation (`make -j$(nproc) Image`)
   Execution jumps directly to artifact verification (`file arch/arm64/boot/Image`) and upload, reducing Job 1 runtime from ~14 minutes to **under 30 seconds**.

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

### 6.4. Real On-Device Verification of the Native Library Exemption
The W^X / `nativeLibraryDir` architecture has been **confirmed genuinely operational on physical Android hardware**:
- Invocations of `execve()` against `/data/app/.../lib/arm64/libqemu_system_aarch64.so` bypass the Android 10+ W^X writable data execution block without permission errors.
- The Android Bionic dynamic linker (`/system/bin/linker64`) successfully starts the binary and initiates dependency resolution against sibling libraries in `nativeLibraryDir`.

### 6.5. Transitive Shared-Library Completeness, Functional Correctness & CI Symbol Closure Guard
With `execve()` execution verified, the remaining runtime risk revolves around transitive shared-library integrity:

1. **The Transitive Dependency Hazard:** `libqemu_system_aarch64.so` and primary libraries link against indirect dependencies (e.g., `libgnutls.so`, which in turn requires `libnettle.so`, `libgmp.so`, `libtasn1.so`, `libp11-kit.so`, `libhogweed.so`, `libunistring.so`, `libidn2.so`, and `libc++_shared.so`). If any single transitive library is absent from `nativeLibraryDir`, the Bionic dynamic linker aborts process execution.
2. **The "Dependency Present" vs. "Dependency Functionally Correct" Distinction:**
   - **Dependency Present (DT_NEEDED Name Matching):** The dynamic linker requires that an ELF shared object matching the exact `DT_NEEDED` name exists in `nativeLibraryDir` (or `$ORIGIN`). A simple file-presence check verifies only that a file with the required name (e.g., `libzstd.so`) is present in the bundle.
   - **Dependency Functionally Correct (Symbol Table Closure):** File presence alone does NOT guarantee functionality. A bundled library may be a build-time stub, a dev-only unversioned linker script (e.g., ASCII text containing `INPUT(libzstd.so.1)`), or an incomplete build artifact lacking required exports (such as `ZSTD_decompress`). When `libqemu_system_aarch64.so` references an undefined symbol (`GLOBAL UND`) that is not exported by any bundled shared object or Bionic, the dynamic linker fails at runtime with an unresolved symbol error. Both "dependency present" and "dependency functionally correct" are fundamentally distinct checks, and both must be enforced.
3. **Recursive Closure Provisioning:** `tools/engine/package-termux-qemu.sh` performs automated recursive resolution by reading `Depends:` trees from the Termux package index and running an iterative ELF `DT_NEEDED` closure loop until all non-system dependencies (`libc.so`, `libm.so`, `libdl.so`, `liblog.so` excluded) are downloaded, converted to `lib*.so`, set to `$ORIGIN` RPATH, and remapped. The provisioner audits every extracted library with `file` to reject ASCII text linker scripts and dev stubs, resolving concrete versioned runtime shared objects (e.g., `libzstd.so.1.*`) instead.
4. **Automated CI Enforcement Guard (Two-Tier Validation in Job 2):**
   - **Tier 1 — ELF Shared Object Audit:** `file` inspects every packaged `.so` in `jniLibs-bundle/arm64-v8a/` to confirm the output indicates a valid `ELF 64-bit ... shared object`, explicitly rejecting ASCII text linker scripts and zero-byte stubs.
   - **Tier 2 — Symbol-Level Closure Guard:** `readelf --dyn-syms` collects all `GLOBAL DEFAULT` exported symbols across the entire bundle, and separately collects all undefined required (`GLOBAL UND`) symbols from each `.so`. Any undefined symbol not provided by Android Bionic (`libc`, `libm`, `libdl`, `liblog`) MUST be satisfied by a library in the bundle. If any required symbol (such as `ZSTD_decompress`) is missing, the CI build fails loudly with a descriptive diagnostic before deployment to physical hardware.

### 6.6. Tripartite Asset Architecture & QEMU Firmware/Option-ROM Resolution (`pc-bios`)

Once the Android 10+ W^X execution restriction is resolved and dynamic symbol closure is satisfied, process launching reveals a third distinct dependency category beyond native executables and the guest kernel Image: **runtime non-executable firmware data files (option-ROMs)**.

#### The Three Asset Classes in AxilBox
1. **Class 1: Native Executable Code (Binaries & Shared Libraries)**
   - **Artifacts:** `libqemu_system_aarch64.so`, `libglib-2.0.so`, `libpixman-1.so`, etc.
   - **Location:** `app/src/main/jniLibs/arm64-v8a/` -> installed to `context.applicationInfo.nativeLibraryDir`.
   - **Execution Model:** Direct `execve()` invocation. Exempt from Android 10+ W^X restrictions because the OS mounts `nativeLibraryDir` with read-execute (`r-x`) permissions.
2. **Class 2: Guest Kernel & Boot Images**
   - **Artifacts:** `Image` (ARM64 virt Linux kernel), `initrd`, disk images (`rootfs.ext4`).
   - **Location:** `app/src/main/assets/kernel/` -> extracted to `context.filesDir/kernel/`.
   - **Execution Model:** Read as plain data by QEMU's `-kernel` / `-initrd` loader.
3. **Class 3: Runtime Non-Executable Firmware & Option-ROM Files**
   - **Artifacts:** QEMU `pc-bios` firmware/ROMs: `efi-virtio.rom`, `efi-e1000.rom`, `efi-e1000e.rom`, `edk2-*.fd`, `keymaps/`, etc.
   - **Location:** `app/src/main/assets/engine/pc-bios/` -> extracted to `context.filesDir/engine/pc-bios/`.
   - **Execution Model:** Read into memory by QEMU's internal romloader via standard file I/O (`open`/`read`/`mmap`) during device initialization.

#### Why Option-ROMs Belong in `assets/`, NOT `jniLibs/`
- **W^X Exemption Scope:** The Android 10+ W^X security restriction only targets writable pages marked executable (`PROT_EXEC`) and `execve()` calls on writable storage. Non-executable data files loaded via `read()` do not require `nativeLibraryDir` placement.
- **AGP Packaging Constraint:** Android Gradle Plugin (AGP) strictly enforces that files in `jniLibs` must follow the `lib<name>.so` shared object pattern. Plain `.rom`, `.bin`, `.fd`, and keymap directory hierarchies cannot be placed in `jniLibs` without violating AGP packaging rules.
- **Clean Separation:** Storing Option-ROMs in `assets/engine/pc-bios/` guarantees they extract to app-private storage (`context.filesDir/engine/pc-bios/`) without polluting the system dynamic linker search space.

#### Root Cause of `failed to find romfile "efi-virtio.rom"` & The `-L` Switch
- **Compiled-in Path Mismatch:** When QEMU initializes VirtIO devices (`virtio-blk-pci`, `virtio-net-pci`, `virtio-gpu-pci`), it automatically attempts to load default PCI Option-ROMs (specifically `efi-virtio.rom`). Upstream Termux QEMU is compiled with a default data prefix hardcoded to `/data/data/com.termux/files/usr/share/qemu`. On standard Android devices running AxilBox (`com.axilbox.app`), this directory does not exist, causing QEMU's romloader to fail with:
  ```text
  qemu-system-aarch64: failed to find romfile "efi-virtio.rom"
  ```
- **The `-L` Resolution Mechanism:** QEMU provides the `-L <dir>` command-line option to override the compiled-in search path for BIOS, VGA BIOS, Option-ROMs, and keymaps. `EngineProvisioner.buildQemuArgs()` and `QemuProcessRunner` explicitly inject:
  ```text
  -L <context.filesDir>/engine/pc-bios
  ```
  allowing QEMU to resolve `efi-virtio.rom` and all subsequent device ROMs from the extracted asset directory on Android storage.
- **Sourcing & CI Enforcement:** The full `pc-bios` tree is extracted directly from the Termux `qemu-common` package alongside `qemu-system-aarch64-headless` in `tools/engine/package-termux-qemu.sh`. Job 2 strictly asserts the presence and non-zero byte size of required default ROMs (`efi-virtio.rom`, `efi-e1000.rom`) before assembling the APK.
