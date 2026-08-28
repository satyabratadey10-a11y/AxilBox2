# RULES.md — Hard Engineering Rules & Constraints

This document defines the strict engineering rules, architectural bans, and operational policies governing all development on AxilBox.

---

## 1. Architectural & Technology Constraints

### 1.1. Zero WebView / Browser Components (Absolute Ban)
- **Constraint:** No `android.webkit.WebView`, GeckoView, Chromium Embedded Framework, HTML canvas, or browser-based rendering/input bridges may exist anywhere in the AxilBox repository, across all phases.
- **Enforcement:**
  - UI must be 100% native Jetpack Compose.
  - Local virtualization display (Phase 2) must route through native JNI/C++ `SurfaceTexture` OpenGL ES pipelines.
  - Cloud streaming (Phase 3) must route through Google's native `libwebrtc` AAR and Android hardware `MediaCodec` Surface decoders.

### 1.2. Zero Bundling of Proprietary Guest OS Content
- **Constraint:** Never commit, bundle, download, or distribute proprietary guest images, Google Mobile Services (GMS), vendor-exclusive binary blobs, or copyrighted firmware.
- **Enforcement:** All guest OS images must be built from open AOSP source or supplied directly by the end user from their own storage via Android SAF.

### 1.3. Zero AVF / pKVM Invocations
- **Constraint:** Do not introduce dependencies on `android.system.virtualmachine` or `MANAGE_VIRTUAL_MACHINE` permissions.
- **Enforcement:** The application must run cleanly as an unprivileged third-party APK on any standard Android 10+ (API 29+) device.

---

## 2. Build, CI & Verification Discipline

### 2.1. GitHub Actions CI Build Policy (No On-Device Builds)
- **Constraint:** No on-device compilation commands (`./gradlew assemble`, `cmake`, `ndk-build`) should be run directly on local host devices.
- **Enforcement:** All compilation, lint verification, unit tests, and APK artifact creation must execute in GitHub Actions CI.

### 2.2. No Self-Reported Success
- **Constraint:** Never declare a task, bug fix, or milestone complete based on personal conjecture or untested assumptions.
- **Enforcement:** Success must be validated by inspecting real GitHub Actions CI workflow logs and verifying successful APK generation and green test suites.

---

## 3. Code Quality, Integrity & Workflow Rules

### 3.1. Cross-File Reference Consistency
- **Constraint:** Prior to concluding any multi-file change, perform a full sanity audit across imports, package declarations, Room schema versions, navigation route arguments, and DI bindings.

### 3.2. Session Visibility & No Silent Background Execution
- **Constraint:** All agent operations, code mutations, and diagnostic steps must be fully logged and visible within the primary session transcript. Never dispatch silent background subagents without transparency.

### 3.3. Model Depth Consistency
- **Constraint:** Maintain full reasoning capability throughout implementation. Never silently downgrade reasoning depth or compromise on architectural completeness.
