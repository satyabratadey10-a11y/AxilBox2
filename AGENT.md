# AGENT.md — Operating Instructions for AI Coding Agents

This document contains mandatory operating instructions for any AI assistant, autonomous coding agent, or automated pair programmer working on the AxilBox repository.

---

## 1. Required Reading & Context Initialization

Before writing, modifying, or proposing any code changes in this repository, you MUST read and internalize the following foundational documents:

1. **`PLAN.md`:** The active roadmap, milestone status, and task breakdown. Never perform work out of sequence or introduce features from later phases ahead of schedule.
2. **`RESEARCHDATA.md`:** The authoritative technical specification for kernel configurations, memory limits, WebRTC video pipelines, and the ARM64 `HWCAP_FLAGM` / `CFINV` hazard.
3. **`RULES.md`:** Non-negotiable engineering rules (no WebViews, no on-device building, verified CI execution, no proprietary image bundling).
4. **`DESIGN.md`:** UI/UX specification, color palette, component design, and screen layouts.
5. **`GOAL.md`:** Project scope and strict legal non-goals (zero iOS, zero proprietary ROMs/GMS, zero AVF dependency).

---

## 2. Mandatory Verification & CI Discipline

### 2.1. Verification via GitHub Actions CI Only
- **Rule:** Never execute on-device Gradle, Java, or NDK compilation commands in local development environments unless explicitly instructed.
- **Rule:** All builds, unit tests, lint checks, and APK packaging MUST run in GitHub Actions CI workflows.
- **Rule:** Never self-report or declare a milestone as "done" or "working" based on local assumptions. Verification must be backed by actual passing CI run logs.

### 2.2. Honesty on Unknowns & Open Questions
- **Rule:** Never invent, hallucinate, or fabricate kernel parameters, hardware capabilities, or JNI behaviors that are not documented in `RESEARCH.md` and `RESEARCHDATA.md`.
- **Rule:** When encountering an architectural ambiguity or undecided design point, explicitly mark it as `TBD` and log it under the Open Questions section of `RESEARCH.md`.

---

## 3. Execution & Workflow Guidelines

1. **Keep Subagent & Tool Work Transparent:** Never hide tool operations or spawn background subagents silently. All changes, file writes, and reasoning must be visible in the main session transcript.
2. **Preserve Model Continuity:** Maintain consistent reasoning depth and never silently degrade to lightweight models during complex multi-file refactors.
3. **Cross-File Consistency:** Before completing any change across multiple files (e.g. Entity, DAO, ViewModel, Screen, NavGraph), verify that all package names, imports, type names, and resource references match exactly.
4. **Clean Code & Modern Android Idioms:**
   - Use Jetpack Compose, Kotlin Coroutines, and `StateFlow` exclusively for UI and state.
   - Use Room with Kotlin KSP for data persistence.
   - Never introduce deprecated Android Support / `v4` / `v7` libraries or Java legacy threads.
   - Strictly avoid `WebView`, HTML rendering, or browser engines anywhere in the codebase.
