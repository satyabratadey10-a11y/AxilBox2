# GOAL.md — Project Mission, Audience & Boundaries

## 1. Project Concept & Mission

**AxilBox** is an open-source native Android application designed to manage, configure, and operate virtual mobile-OS instances directly on or streamed to Android devices — similar in spirit and functionality to Oracle VM VirtualBox or VMware Workstation, but engineered specifically for mobile computing environments and touch-first form factors.

The mission of AxilBox is to provide a fully native, local-first virtualization management environment where users can create named guest instances, specify hardware parameters (CPU core allocation, RAM allocation, storage sizing, OS kernel/image paths, and peripheral profiles), boot and interact with guest operating systems, and install or debug software within isolated guest environments.

---

## 2. Target Audience & Educational Objectives

AxilBox is engineered for a broad community of developers, security researchers, systems programmers, and technical learners:

- **Systems Programmers (C, C++, Rust, Assembly):** Learners exploring low-level ARM64 architecture, bare-metal boot flows, kernel device trees (DTS), virtio device drivers, JIT dynamic translation internals, and systems-level memory management.
- **Mobile & OS Developers (Kotlin, Java, AOSP):** Engineers developing Android applications, exploring AOSP internals, customizing system services (SurfaceFlinger, Binder, AudioFlinger, HALs), and testing system builds across multiple API levels on a single physical host.
- **Ethical Hackers & Security Researchers:** Professionals and students conducting mobile security assessments, malware analysis, reverse engineering, and isolated fuzzing inside contained sandbox environments without risking host device integrity.
- **Computer Science Students & Educators:** Academic learners studying operating systems theory, virtualization models (hypervisor vs. dynamic binary translation vs. remote container streaming), paging, memory virtualization, and hardware abstraction.

---

## 3. Explicit Non-Goals & Legal Boundaries (Strict & Non-Negotiable)

The following boundaries are foundational, architectural, and permanent. No phase of AxilBox will violate or bypass these constraints:

### 3.1. Zero iOS / Apple OS Support
- **Constraint:** AxilBox will never support, emulate, or run iOS, iPadOS, watchOS, macOS, or any Apple proprietary operating system.
- **Rationale:** Apple’s end-user software license agreements (EULA), proprietary boot chain cryptographic enforcement, signed Secure Enclave firmware, and intellectual property restrictions make running Apple OS software on non-Apple hardware legally non-viable and ethically outside the scope of open-source tooling, regardless of technical feasibility.

### 3.2. Zero Bundling of Proprietary Guest Images or Firmware
- **Constraint:** AxilBox will not distribute, bundle, download from unverified third parties, or pre-package any proprietary guest OS images, Google Mobile Services (GMS / Google Play Services), vendor proprietary hardware blobs, console ROMs, or copyrighted device firmware.
- **Rationale:** Strict compliance with copyright and distribution laws. All guest operating systems must either be built from source by the user (e.g., open-source AOSP `gsi_arm64-userdebug` virt targets, generic upstream Linux distributions such as Debian/Alpine/Arch ARM64) or legally supplied and loaded by the user from their own local storage via Android Storage Access Framework (SAF).

### 3.3. Zero Dependency on Android Virtualization Framework (AVF / pKVM)
- **Constraint:** AxilBox will not rely on Android's built-in `android.system.virtualmachine` / AVF / pKVM APIs for its core virtualization architecture.
- **Rationale:** Architectural audit confirms that AVF requires the `MANAGE_VIRTUAL_MACHINE` permission, which is strictly restricted to platform-signed applications or system OEM signatures (`signature` protection level). It is completely unobtainable by third-party applications under any standard unrooted Android runtime configuration. AxilBox must remain functional as a standard, unprivileged Android application.

### 3.4. Zero WebView, HTML, or Browser Render/Input Path
- **Constraint:** No `WebView`, HTML5 canvas, browser component, or JavaScript-based DOM engine may exist anywhere in the rendering or input forwarding pipeline at any phase.
- **Rationale:** The entire user interface must be implemented in 100% native Jetpack Compose for Android. The virtualization display pipeline in Phase 2 utilizes native JNI/C++ copying guest framebuffers into Android `SurfaceTexture` / OpenGL ES external textures, and Phase 3 utilizes native Google `libwebrtc` AAR feeding hardware `MediaCodec` surface decoders directly into a native `SurfaceViewRenderer`. Eliminating WebViews guarantees deterministic frame delivery, zero WebKit memory bloat, native input latency, and tight integration with Android windowing.
