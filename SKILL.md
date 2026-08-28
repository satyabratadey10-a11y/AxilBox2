# SKILL.md — Contributor Technical Skill Inventory & Learning Path

This document catalogs the technical domains, languages, APIs, and systems concepts utilized across each development phase of AxilBox. It serves as both a contributor competence guide and a structured learning curriculum.

---

## 1. Phase 1: Modern Android Architecture & Native Compose UI

Contributors working on Phase 1 should possess or develop proficiency in the following technologies:

| Domain | Key Concepts & APIs | Recommended Study Resources |
|---|---|---|
| **Kotlin Core & Idioms** | Data classes, sealed interfaces, extension functions, standard library scoping | Official Kotlin Documentation |
| **Asynchronous Programming** | Kotlin Coroutines, `StateFlow`, `SharedFlow`, `viewModelScope`, structured concurrency | Android Developers: Kotlin Coroutines & Flow |
| **Declarative UI** | Jetpack Compose, Material Design 3, custom modifiers, canvas drawing, animation physics | Android Compose Pathways |
| **Persistence Layer** | Android Jetpack Room, SQLite, Kotlin Symbol Processing (KSP), DAOs, migrations | Room Persistence Guide |
| **Architecture & Navigation** | Unidirectional Data Flow (UDF), MVVM pattern, Type-Safe Navigation 2.8+ | Android App Architecture Guide |
| **Scoped Storage** | Storage Access Framework (SAF), Document Providers, persistable URI permissions | Scoped Storage in Android |

---

## 2. Phase 2: Systems Programming, JNI & QEMU Internals (Future PoC)

Contributors venturing into Phase 2 on-device QEMU bring-up should study:

| Domain | Key Concepts & APIs | Recommended Study Resources |
|---|---|---|
| **C & C++17/20** | Pointer arithmetic, memory layout, POSIX system calls, signal handling, atomics | "The Linux Programming Interface" by Michael Kerrisk |
| **Android NDK & JNI** | JNIEnv method bindings, direct byte buffers, native thread lifecycle, CMake build scripts | Android NDK Documentation |
| **ARM64 Assembly** | Registers (`x0`-`x30`, `sp`, `pc`), condition flags (`NZCV`), FlagM instructions (`CFINV`), inline assembly | ARMv8-A Architecture Reference Manual |
| **QEMU Internals** | Tiny Code Generator (TCG), `virt` board initialization, virtio-pci/virtio-mmio device emulation | QEMU Developer Documentation & Source Code |
| **Linux Kernel & Device Trees** | ARM64 DTS (Device Tree Source), UART PL011 drivers, initramfs packing, ext4 filesystems | Linux Kernel Documentation (kernel.org) |
| **Android Native Graphics** | `SurfaceTexture`, OpenGL ES 2.0/3.0 external textures (`GL_TEXTURE_EXTERNAL_OES`), EGL contexts | Android Graphics Architecture (source.android.com) |

---

## 3. Phase 3: Cloud Virtualization, WebRTC & Low-Latency Streaming (Future)

Contributors working on Phase 3 cloud infrastructure and streaming client should study:

| Domain | Key Concepts & APIs | Recommended Study Resources |
|---|---|---|
| **WebRTC Architecture** | PeerConnection, SDP offer/answer negotiation, ICE/STUN/TURN, SRTP media encryption | "WebRTC: APIs and RTCWEB Protocols" & webrtc.org |
| **Native Google `libwebrtc`** | C++ WebRTC client integration, `org.webrtc.SurfaceViewRenderer`, `VideoSink`, `DataChannel` | Chromium WebRTC Native Source Code |
| **Android Hardware Codecs** | `android.media.MediaCodec` in surface mode, low-latency decoding flags, NAL unit extraction | Android MediaCodec Documentation |
| **Linux Virtualization** | KVM (Kernel-based Virtual Machine), GPU passthrough / vGPU (NVIDIA GRID / VFIO), headless X11/Wayland | QEMU/KVM Documentation & Selkies Project |
| **Linux Input Subsystem** | Linux `uinput` kernel driver, `struct input_event`, multi-touch protocol slot management | Linux Input Subsystem Documentation |
| **Network Protocols** | RTP/RTCP, SCTP, TWCC (Transport-Wide Congestion Control), jitter buffer management | RFC 3550, RFC 8829 |
