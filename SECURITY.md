# SECURITY.md — Threat Model & Security Architecture

This document establishes the security architecture, threat model, and defense-in-depth principles for AxilBox across all development phases.

---

## 1. Phase 1 Threat Model (Local-First Shell)

In Phase 1, AxilBox operates as a completely local, self-contained instance manager with zero external network connectivity.

### 1.1. Permissions Footprint
- **Permissions Requested:** Zero dangerous or privileged permissions.
  - No `INTERNET` permission in Phase 1 manifest.
  - No `READ_EXTERNAL_STORAGE` / `MANAGE_EXTERNAL_STORAGE` broad permissions.
  - No root or Superuser permissions.
- **Principle of Least Privilege:** Storage access is strictly mediated by Android's Storage Access Framework (SAF) using scoped `Intent.ACTION_OPEN_DOCUMENT` and `takePersistableUriPermission`. AxilBox never gains broad filesystem traversal rights.

### 1.2. Local Data Isolation
- **Room Database Security:** SQLite/Room database is stored exclusively in the app's private sandbox directory (`/data/user/0/com.axilbox.app/databases/`), protected by standard Linux UID/GID isolation.
- **No Remote Telemetry:** No third-party tracking, analytics, or remote telemetry SDKs are embedded in the app.

---

## 2. Forward-Looking Security Model (Phases 2 & 3)

As virtualization backends and streaming capabilities are introduced in subsequent phases, the following security guarantees must be preserved:

### 2.1. Phase 2: Local Guest Sandboxing
- **Unprivileged Userspace Boundary:** The QEMU emulation layer runs entirely within the unprivileged Android application sandbox UID. It possesses no capabilities beyond ordinary app permissions.
- **Zero Root Claims:** AxilBox makes no requirement or assumption of host root access.
- **Disk Isolation:** Guest disk images are either sandboxed in internal storage or SAF-mounted files. Malicious guest kernel payloads cannot break out of QEMU's software memory translation to compromise host Android memory.

### 2.2. Phase 3: Cloud WebRTC & Input Bridge Hardening
- **Cryptographic Channel Protection:** All video, audio, and data streams route over DTLS 1.2+ and SRTP with AES-GCM-256 encryption.
- **Authenticated Input Bridge:** The remote input forwarding channel (WebRTC DataChannel -> guest `uinput`) must be cryptographically authenticated with short-lived session tokens to prevent unauthorized keystroke or touch injection attacks.
- **No Open Inbound Ports on Client:** Android client operates strictly as an outbound WebRTC peer connection initiator. No listening server sockets or ADB daemon ports are opened on the host device.
- **Input Sanitization:** Remote MotionEvents forwarded to the cloud VM input bridge must be strictly bounded to guest screen coordinate dimensions to prevent integer overflow or out-of-bounds input event crashes.

### 2.3. GPLv2 Binary Sandboxing & Open-Source Redistribution Compliance
- **GPLv2 Source-Availability Mandate:** QEMU and the Linux kernel are governed by the GNU General Public License v2 (GPLv2). If AxilBox distributes bundled QEMU binaries (`qemu-system-aarch64`) or compiled kernel artifacts (`Image`) within or alongside the application package, Section 3 of GPLv2 mandates that complete corresponding source code must be made accessible under the same terms.
- **Execution Sandboxing & Native Library Packaging:** Bundled QEMU binaries and supporting shared libraries are packaged following Android's `jniLibs` convention (`libqemu_system_aarch64.so` and `lib*.so`), extracted to the app's `nativeLibraryDir` at install time with `android:extractNativeLibs="true"` to comply with Android 10+ W^X execution restrictions, and executed exclusively as child processes under the application's unprivileged UID. No shared-memory or inter-process IPC channels are exposed to other applications on the device.
- **Supply Chain Reproducibility:** All bundled binaries are provisioned through independent, verifiable CI workflows (direct Linux `kbuild` and official Termux cross-compilation recipes) without unverified third-party blobs.

