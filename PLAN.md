# PLAN.md — Phased Architecture & Execution Roadmap

This document outlines the phased roadmap for AxilBox, tracking implementation progress across all development milestones.

---

## Roadmap Overview

- [x] **Phase 0: Legal, Architectural & Scope Specification**
  - [x] Legal boundaries established (zero Apple OS, zero proprietary blobs/GMS, open AOSP/user-supplied images only).
  - [x] AVF/pKVM architectural evaluation concluded (inaccessible to 3rd-party unprivileged apps).
  - [x] Zero-WebView architectural mandate enacted.
  - [x] Multi-phase technical roadmap defined.

- [x] **Phase 1 (CURRENT): Native Compose UI Shell & Room-Backed Instance Management**
  - [x] **Milestone 1.1: Data Architecture & Persistence Layer**
    - [x] Define `VirtualInstance` entity (id, name, osType, vCpuCount, ramMb, storageGb, imageUri, kernelUri, initrdUri, extraCmdline, status, createdAt, lastBootedAt).
    - [x] Create Room DAO (`InstanceDao`) with reactive Flow queries (`getAllInstances`, `getInstanceById`, `insert`, `update`, `delete`, `updateStatus`).
    - [x] Configure `AxilBoxDatabase` with Room migration strategies and pre-populated default templates (e.g. AOSP Minimal ARM64, Debian ARM64, Custom).
    - [x] Build clean repository layer (`InstanceRepository`) abstracting data access.
  - [x] **Milestone 1.2: Architecture, State & Navigation**
    - [x] Implement Jetpack Navigation 2.8+ type-safe navigation routes (`MainMenu`, `AddInstance`, `BootScreen/{instanceId}`).
    - [x] Implement `InstanceViewModel` with `StateFlow<InstanceUiState>` for unidirectional data flow (UDF).
    - [x] Integrate system resource query utility (`SystemResourceProvider`) to display physical host RAM and available storage.
  - [x] **Milestone 1.3: Main Menu Screen (Instance Dashboard)**
    - [x] Top App Bar with AxilBox branding, Host Resource Utilization badge, and quick actions.
    - [x] Virtual instance list displaying rich cards (Instance Name, OS badge, CPU/RAM/Storage specs, status indicator, last booted timestamp).
    - [x] Instance action controls per card (Start/Boot, Quick Settings, Edit, Delete with confirmation dialog).
    - [x] Interactive Empty State with visual guidance when zero instances exist.
    - [x] Floating Action Button (FAB) for fast instance creation.
  - [x] **Milestone 1.4: Add / Edit Instance Screen**
    - [x] Clean form layout with real-time field validation (unique name check, memory constraints).
    - [x] OS Profile presets (AOSP ARM64, Generic Linux, Custom Raw Image).
    - [x] Interactive sliders with haptic step feedback for vCPUs (1–4), RAM (512MB–4096MB), and Storage (4GB–64GB) with visual memory budget warnings.
    - [x] SAF-backed file picker stubs for selecting disk image, kernel, and initrd files.
    - [x] Collapsible advanced accordion for kernel command-line arguments and display orientation defaults.
    - [x] Instant save/cancel actions with database commit and seamless navigation back.
  - [x] **Milestone 1.5: Boot Screen (Phase 1 Simulated Telemetry & Execution Stub)**
    - [x] Dynamic Header with instance specs, running timer, and real-time state badge (STOPPED, BOOTING, RUNNING).
    - [x] Guest Display Container mimicking physical device aspect ratio (9:16 portrait / 16:9 landscape) with simulated active frame rasterizer.
    - [x] Live Monospace Boot Telemetry Console rendering sequential boot stages (DTS load, PL011 UART init, virtio-pci probe, ext4 mount, SurfaceFlinger stub).
    - [x] Interactive guest toolbar (Power Off / ACPI shutdown, Restart, Rotate Display, Capture Frame, Terminal Log Pause/Copy).
  - [x] **Milestone 1.6: Visual Design System, Dark Mode & Polish**
    - [x] Implement dark-mode design system with deep slate/zinc backgrounds, glassmorphism surface tokens, and neon status accents.
    - [x] Smooth screen transitions and card expansion animations using Compose animation physics.
    - [x] Comprehensive unit tests for Room DAOs, ViewModels, and UI state reducers.
    - [x] GitHub Actions CI workflow for spotless check, unit tests, and debug APK artifact generation.

---

- [ ] **Phase 2 (Future, Proof-of-Concept Only — Local QEMU `virt` On-Device)**
  - *Note: Non-shippable proof-of-concept to evaluate raw on-device ARM64 dynamic translation and virtio graphics performance.*
  - [ ] **Bring-Up Step 2.1:** Serial-only generic ARM64 Linux kernel boot over unprivileged userspace QEMU backend without display/input.
  - [ ] **Bring-Up Step 2.2:** Add `virtio-blk` disk driver support and rootfs mounting.
  - [ ] **Bring-Up Step 2.3:** Add `virtio-gpu` 2D display pipeline using software rasterization (minigbm/swiftshader) without hardware GPU virtualization.
  - [ ] **Bring-Up Step 2.4:** Add input subsystem via emulated tablet device, followed by native Android `MotionEvent` JNI forwarding to `virtio-input`.
  - [ ] **Bring-Up Step 2.5:** Boot specialized Android userspace built directly from AOSP source (`gsi_arm64-userdebug` target compiled with virtio HALs).
  - [ ] **Framebuffer Pipeline Integration:** Native C++ bridge reading guest framebuffer -> CPU copy/convert -> Android `SurfaceTexture` -> External GL texture rendering on dedicated GL render thread.
  - [ ] **Memory & Core Tuning:** Enforce strict 2–3 GB guest memory ceiling and 2–4 vCPU cap on 6GB host hardware.

---

- [ ] **Phase 3 (Future, Primary Production Product — Cloud-Hosted VM with Native WebRTC)**
  - *Reference Architecture: Selkies (github.com/selkies-project/selkies)*
  - [ ] **Cloud VM Backend:** Real QEMU/KVM instances with dedicated hardware GPU acceleration (NVIDIA/AMD vGPU).
  - [ ] **Streaming Pipeline:** VM Display Capture Bridge -> Ultra-low latency H.264 Encoder (zero B-frames, 1s IDR GOP, slice-based intra-refresh) -> WebRTC Media Track -> Direct RTP/SRTP transmission.
  - [ ] **Native Android Client:** Google `libwebrtc` native AAR -> Android Hardware `MediaCodec` Surface Mode decoder -> Native `SurfaceViewRenderer` (100% native, zero WebView).
  - [ ] **Input Pipeline:** Android `MotionEvent` -> Native JNI -> WebRTC DataChannel (low latency SCTP/UDP) -> Authenticated Server-Side Input Bridge -> Linux/Android guest `uinput` kernel device.
  - [ ] **P2P Architecture:** One VM per user session with direct peer-to-peer WebRTC connection (no SFU overhead).
  - [ ] **Debug Console:** Secondary authenticated VNC/raw socket channel exclusively for low-level serial debug and crash logs.

---

- [ ] **Phase 4 (Future — Developer & Automation Tooling)**
  - [ ] ADB-over-network bridge forwarding to guest instances.
  - [ ] Automated testing integration (Appium, UiAutomator2, and custom Python/Kotlin test harness execution inside guests).
  - [ ] Snapshot and rollback management for guest disk states.

---

- [ ] **Phase 5 (Future — Packaging & Distribution)**
  - [ ] Direct APK / GitHub Releases sideload distribution pipeline.
  - [ ] Google Play Store compliance audit (permissions scrutiny, SAF sandboxing, zero bundled proprietary content verification).
