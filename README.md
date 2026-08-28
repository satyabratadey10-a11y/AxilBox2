# AxilBox

[![Android CI](https://github.com/satyabratadey10-a11y/AxilBox/actions/workflows/android-ci.yml/badge.svg)](https://github.com/satyabratadey10-a11y/AxilBox/actions)
[![License: Apache 2.0](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android_10+-brightgreen.svg)](https://android.com)

**AxilBox** is an open-source native Android application for managing and operating virtual mobile-OS instances directly on or streamed to Android devices — inspired by Oracle VirtualBox, but reimagined for mobile computing.

AxilBox empowers developers, security analysts, and systems programming learners to configure, boot, and inspect isolated virtual instances (such as AOSP and ARM64 Linux) with dedicated RAM, vCPU, and disk allocations.

---

## Current Status: Phase 1 (Native Compose UI & Room Management)

AxilBox is actively in **Phase 1** development:
- **UI Architecture:** 100% Native Jetpack Compose with Material Design 3 engineering dark-mode theme. Zero WebViews anywhere in the render path.
- **Data Persistence:** Local Room database with reactive Kotlin `StateFlow` streams.
- **Screens:**
  1. **Main Menu:** Instance dashboard displaying virtual machine cards, real-time host resource utilization, and action controls.
  2. **Add / Edit Instance:** Hardware parameter configuration with validation (vCPUs, RAM slider, Storage sizing, and SAF disk pickers).
  3. **Boot Screen (Phase 1 Stub):** High-tech telemetry console streaming simulated boot sequences and execution controls.

---

## Project Roadmap

| Phase | Description | Status |
|---|---|---|
| **Phase 0** | Legal, Scope & Boundary Specification | Completed |
| **Phase 1** | Native Compose UI Shell + Room Instance Manager | **Active / In Progress** |
| **Phase 2** | Local ARM64 QEMU `virt` On-Device Bring-Up (Proof-of-Concept) | Planned |
| **Phase 3** | Cloud VM with Hardware GPU & Native WebRTC Streaming | Planned (Primary Product) |
| **Phase 4** | ADB Network Forwarding & Automated Guest Testing | Planned |
| **Phase 5** | Public Packaging & Play Store Deployment | Planned |

---

## Project Documentation Index

- [`GOAL.md`](GOAL.md) — Project mission, audience, and strict non-goals (zero Apple OS, zero proprietary ROMs, zero AVF dependency).
- [`PLAN.md`](PLAN.md) — Full phased technical roadmap and milestone checklist.
- [`DESIGN.md`](DESIGN.md) — UI/UX specification, color palette, component design, and screen layouts.
- [`RESEARCH.md`](RESEARCH.md) — Research findings (AVF accessibility, ARM64 `virt` bring-up, WebRTC streaming) and open questions.
- [`RESEARCHDATA.md`](RESEARCHDATA.md) — Low-level technical reference (kernel config flags, WebRTC pipeline, ARMv8 `HWCAP_FLAGM` hazard, memory budgets).
- [`AGENT.md`](AGENT.md) — Operating guidelines and constraints for AI coding agents.
- [`RULES.md`](RULES.md) — Hard engineering rules and architecture constraints.
- [`SECURITY.md`](SECURITY.md) — Threat model, permission isolation, and sandboxing architecture.
- [`SKILL.md`](SKILL.md) — Contributor technical skill inventory and learning curriculum.
- [`CLAUDE.md`](CLAUDE.md) — Instructions for Claude Code sessions.
- [`GEMINI.md`](GEMINI.md) — Instructions for Gemini and Google AI Studio sessions.

---

## Building & Verification

In accordance with [`RULES.md`](RULES.md), AxilBox builds, tests, and packages exclusively via **GitHub Actions CI**. Local on-device compilation is prohibited to maintain clean reproducible environments.

### CI Workflow
All commits and pull requests trigger `.github/workflows/android-ci.yml`, which executes:
1. Kotlin Symbol Processing & Room Schema Validation
2. Unit Test Suite Execution
3. Android Lint & Code Quality Verification
4. Debug APK Compilation and Artifact Upload

You can view active builds and download compiled debug APKs on the [GitHub Actions tab](https://github.com/satyabratadey10-a11y/AxilBox/actions).

---

## Repository Structure

```
AxilBox2/
├── .github/
│   └── workflows/
│       └── android-ci.yml           # GitHub Actions CI pipeline
├── app/
│   ├── build.gradle.kts             # Module build configuration
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/axilbox/app/
│       │   │   ├── AxilBoxApplication.kt
│       │   │   ├── MainActivity.kt
│       │   │   ├── data/            # Room Entity, DAO, Database & Repository
│       │   │   ├── model/           # Data models & Enums (OsType, InstanceStatus)
│       │   │   ├── ui/
│       │   │   │   ├── components/  # Reusable Compose widgets & badges
│       │   │   │   ├── navigation/  # Type-safe Jetpack Navigation Graph
│       │   │   │   ├── screens/     # MainMenu, AddInstance, BootScreen
│       │   │   │   ├── theme/       # Dark-mode color palette, type & shapes
│       │   │   │   └── viewmodel/   # InstanceViewModel & UI State
│       │   │   └── util/            # SystemResourceProvider & Telemetry
│       │   └── res/                 # App icons, strings, and drawables
│       └── test/                    # Unit tests for DAOs, ViewModels & State
├── gradle/                          # Gradle wrapper & version catalogs
├── build.gradle.kts                 # Root project build configuration
├── settings.gradle.kts              # Project settings & repositories
├── gradle.properties                # Build performance flags
└── [Documentation Set: GOAL.md, PLAN.md, DESIGN.md, etc.]
```

---

## License

AxilBox is released under the [Apache License, Version 2.0](LICENSE).
