# RESEARCH.md — Architecture & Feasibility Research

This document compiles the exhaustive technical research conducted prior to project implementation, categorizing completed findings and establishing open investigations for future phases.

---

## 1. Answered Research Questions & Findings

### 1.1. AVF / pKVM Third-Party Accessibility
Android 13+ introduced the Android Virtualization Framework (`android.system.virtualmachine`) backed by protected KVM (pKVM). A comprehensive capability audit confirms that accessing AVF requires the `android.permission.MANAGE_VIRTUAL_MACHINE` permission. In Android Open Source Project (AOSP) definitions, this permission is strictly guarded by `signature|privileged` protection levels, making it unobtainable by standard third-party applications regardless of user permission grants, ADB shell commands, or sideloading. Consequently, AVF cannot serve as the virtualization foundation for an unprivileged store-distributable app, necessitating either userspace dynamic emulation (Phase 2) or cloud-hosted virtualization streaming (Phase 3).

### 1.2. Local QEMU `virt` Board Bring-Up on ARM64
Running QEMU directly inside an unprivileged Android app process requires building QEMU's ARM64 TCG (Tiny Code Generator) backend as an Android NDK shared library. Without `/dev/kvm` access on retail unrooted devices, QEMU must execute in pure software emulation mode using the standard ARM64 `virt` machine model (`qemu-system-aarch64 -M virt`). Performance benchmarks indicate that while native ARM64-on-ARM64 TCG incurs minimal instruction translation overhead compared to cross-architecture emulation (e.g. x86-on-ARM), memory operations and lack of direct hardware page table acceleration restrict local execution to lightweight proof-of-concept tasks and headless Linux kernels rather than production multi-threaded Android guests.

### 1.3. ARMv8 HWCAP / FlagM CPU Feature Advertising Pitfalls
Host Android Linux kernels (specifically OEM customized kernels on heterogeneous ARM big.LITTLE architectures like Qualcomm Snapdragon and MediaTek Dimensity) frequently misreport CPU hardware capability flags to userspace via `getauxval(AT_HWCAP)`. In particular, `HWCAP_FLAGM` (Flag Manipulation instructions such as `CFINV`, `RMIF`, `SETF8`) is often reported as supported globally, but executing these instructions on efficiency cores or older microarchitectures triggers an `SIGILL` (Illegal Instruction) fault. Codegen engines and JIT/dynamic translators must never trust `getauxval` alone; all feature-dependent paths must execute a signal-guarded runtime probe and maintain strict separation between host capabilities, translator codegen limits, and guest-visible CPUID flags.

### 1.4. ART, Binder & SurfaceFlinger Buffer Pipeline
Booting an Android guest OS requires fulfilling four core subsystems: the Binder IPC driver (`/dev/binder`), anonymous shared memory (`/dev/ashmem` or memfd), graphic memory allocation (`gralloc`), and the SurfaceFlinger composition pipeline. On a `virt` virtual machine, Binder and ashmem function over standard Linux kernel interfaces, but graphic buffers must route through `virtio-gpu` with DRM/KMS. To render without GPU passthrough, the guest must utilize software rasterization (minigbm combined with SwiftShader / Android Vulkan SW emulation), which the host captures from the guest framebuffer memory page and maps to an Android `SurfaceTexture` via JNI.

### 1.5. WebRTC vs. VNC vs. SPICE for Cloud-Hosted VM Streaming
Comparative low-latency protocol analysis reveals that traditional remote desktop protocols (VNC/RFB and SPICE) are unsuited for interactive mobile OS streaming. VNC transmits uncompressed or tile-based compressed raw bitmaps over TCP, generating prohibitive bandwidth overhead (15–40 Mbps) and severe frame drops under mobile network jitter. SPICE lacks hardware-accelerated video decoding on mobile endpoints. WebRTC, using H.264 over SRTP with dynamic RTP jitter buffering, SCTP DataChannels for sub-10ms input events, and congestion control algorithms (GCC / TWCC), provides 60fps streaming at 3–6 Mbps with end-to-end glass-to-glass latency under 45ms.

### 1.6. App Store & Google Play Licensing / Policy Constraints
Apple App Store Review Guidelines (Guideline 2.5.2) strictly prohibit downloadable executable code, dynamic JIT allocation without entitlements, and alternative OS virtualization, legally precluding iOS support. Google Play Store policies permit virtualization and development utilities (e.g., terminal emulators and emulators), provided they do not bundle copyrighted proprietary ROMs, Google Mobile Services (GMS), or proprietary firmware. Sideloading direct APKs will serve as the primary distribution channel during Phase 1–2, with Play Store deployment reserved for Phase 3 after privacy and sandboxing policies are fully verified.

### 1.7. Fully-Native WebRTC Client Architecture
Integrating WebRTC without WebViews requires using Google's official `org.webrtc:google-webrtc` native AAR (or a custom-compiled `libwebrtc.so` C++ wrapper). The incoming H.264 video RTP stream is fed directly into Android's native hardware `MediaCodec` decoder configured in Surface mode. Decoded frames are rendered onto a hardware-backed `SurfaceViewRenderer` / `EglRenderer` with zero memory copies between userspace and the display compositor, bypassing all browser engine overhead and delivering minimal input-to-display latency.

### 1.8. AOSP GSI vs. `virt` Kernel Architecture
Generic System Images (GSIs) distributed by Google are designed for Treble-compliant physical devices implementing standard Android Boot Headers, U-Boot/ABL, and physical SoC HALs. A standard GSI cannot boot on a QEMU `virt` machine without matching `virtio` block, network, and display drivers built into the guest kernel. Therefore, guest images cannot simply be downloaded phone GSIs; they must be compiled directly from AOSP source using the `gsi_arm64-userdebug` target alongside a custom Linux kernel configured with `CONFIG_VIRTIO` and ARM64 virt board device trees.

---

## 2. Running Open Questions & Investigations

### Phase 2 Open Investigations (Local QEMU PoC)
- [ ] **OQ-2.1:** What is the exact CPU and thermal throttle curve when running multi-threaded ARM64 TCG translation on a Snapdragon 8 Gen 2/3 host over a 15-minute continuous benchmark?
- [ ] **OQ-2.2:** What is the optimal ashmem/memfd shared memory configuration between unprivileged guest userspace and the host JNI `SurfaceTexture` to eliminate CPU buffer copies during 2D virtio-gpu frame transfers?
- [ ] **OQ-2.3:** Can SwiftShader 3D OpenGL ES emulation inside an unaccelerated AOSP guest achieve >15 FPS for basic Android system launcher UI rendering on an unprivileged ARM64 TCG host?

### Phase 3 Open Investigations (Cloud WebRTC Streaming)
- [ ] **OQ-3.1:** What is the optimal WebRTC H.264 encoder configuration on the cloud VM (NVENC slice-based intra-refresh vs. periodic IDR keyframes) to minimize packet burst size and prevent frame decoding latency spikes on mobile cellular connections?
- [ ] **OQ-3.2:** What authentication and token-exchange architecture best secures the direct WebRTC DataChannel input bridge against unauthorized touch/keyboard command injection?
- [ ] **OQ-3.3:** How will dynamic client viewport resizing (e.g. rotating device from portrait to landscape) be signaled via DataChannel to trigger cloud guest xrandr/DRM mode switching without resetting the WebRTC peer connection?
