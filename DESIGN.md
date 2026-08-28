# DESIGN.md — UI/UX & Visual Engineering Specification

This document defines the complete visual design language, interface architecture, design tokens, and screen-by-screen specifications for AxilBox.

---

## 1. Visual Design Language & Aesthetics

AxilBox adopts an engineering-first, modern dark-mode aesthetic inspired by precision developer environments (e.g., modern IDEs, hypervisor consoles, and low-latency streaming dashboards). The visual system prioritizes clarity, information density, high contrast, crisp borders, and fluid feedback.

### 1.1. Color Palette (Pure Black & Solid White Monochrome)

| Token Name | Hex Code | Semantic Role |
|---|---|---|
| `Background` | `#000000` | Pure black canvas background everywhere |
| `SurfacePrimary` | `#000000` | Card, container, and dialog background |
| `SurfaceSecondary` | `#000000` | Interactive input fields, secondary containers |
| `BorderWhite` | `#FFFFFF` | 1.5–2dp crisp white structural outline and card borders |
| `ButtonWhite` | `#FFFFFF` | Solid white fill for primary buttons, FAB, save/create actions |
| `ButtonTextBlack` | `#000000` | Solid black text and icon inside white buttons |
| `TextPrimary` | `#FFFFFF` | Main headings, instance titles, primary labels, values |
| `TextSecondary` | `#CCCCCC` | Subtitles, helper text, inactive labels, specs |
| `TextMuted` | `#888888` | Timestamp tags, placeholder text, hints |
| `StatusBadge` | `#000000` / `#FFFFFF` | Black fill with 1.5dp white outline and white text (no color-coding) |
| `ChipSelected` | `#FFFFFF` | Solid white fill with black text |
| `ChipUnselected` | `#000000` | Black fill with 1.5dp white outline and white text |
| `TerminalBg` | `#000000` | Pure black monospace live log console background |
| `TerminalText` | `#FFFFFF` | Monospace log stream output |

### 1.2. Typography Hierarchy

The typography system uses Android system sans-serif (`Roboto` / `Inter` fallback) for interface elements and system monospace (`Roboto Mono` / `Courier` fallback) for telemetry, RAM/CPU allocation metrics, and boot logs.

- **Display Large:** `28sp`, Bold, LineHeight `34sp` — Top level screen titles (`#FFFFFF`).
- **Title Medium:** `18sp`, SemiBold, LineHeight `24sp` — Instance card titles, modal headings (`#FFFFFF`).
- **Body Regular:** `14sp`, Normal, LineHeight `20sp` — Descriptive text, form labels (`#FFFFFF`).
- **Body Small:** `12sp`, Normal, LineHeight `16sp` — Secondary hints, status labels (`#CCCCCC`).
- **Monospace Code:** `12sp`, SemiBold / Normal, LineHeight `16sp` — Memory sizes (e.g. `2048 MB`), vCPU counts, boot log stream (`#FFFFFF`).

### 1.3. Iconography & Visual Assets
- Icon library: Material Symbols / Lucide Icons via Compose standard icons.
- Style: Consistent 2.0dp stroke outline style, monochrome white icons (black when inside solid white buttons). No emoji, no color icons.
- Sizing Tokens:
  - `IconSmall`: `16dp` — In-chip status icons, small badge markers.
  - `IconMedium`: `20dp` — Button leading icons, action controls.
  - `IconLarge`: `24dp` — Top app bar navigation and primary menu actions.
  - `IconHero`: `48dp` — Empty state banners and boot logo containers.

### 1.4. Corner Radii & Elevation
- `RadiusSmall`: `8dp` — Chips, badges.
- `RadiusMedium`: `12dp` — Text input fields, dropdown menus.
- `RadiusLarge`: `16dp` — Cards, action buttons, dialog modals, resource summary panel.
- Surface Elevation: 0dp elevation with 1.5dp `#FFFFFF` border stroke on pure black `#000000` surfaces.

### 1.5. Motion & Physics
- Screen transition duration: `220ms` with `FastOutSlowInEasing`.
- Interactive state press scale: `0.98x` scale spring feedback on card click.
- Status pulse animation: `800ms` infinite pulse on `BOOTING` badge indicator.

---

## 2. Screen-by-Screen Interface Specification

```
   ┌─────────────────────────────────────────────────────────┐
   │                    AXILBOX NAVIGATION                   │
   └─────────────────────────────────────────────────────────┘
                                │
        ┌───────────────────────┼───────────────────────┐
        ▼                                               ▼
┌────────────────────────┐                    ┌────────────────────────┐
│   SCREEN 1: MAIN MENU  │ ─── [Tap Add] ───► │  SCREEN 2: ADD / EDIT  │
│ (Instance Dashboard)   │ ◄── [Save/Cancel]  │       INSTANCE         │
└────────────────────────┘                    └────────────────────────┘
        │
        ▼ [Tap Boot / Start]
┌────────────────────────┐
│  SCREEN 3: BOOT SCREEN │
│  (Telemetry & Display) │
└────────────────────────┘
```

---

### 2.1. Screen 1: Main Menu (Instance Dashboard)

#### Top App Bar
- **Title:** "AxilBox" with a glowing cyan terminal icon.
- **Host Resource Summary Pill:** Real-time host memory indicator (e.g., `Host: 3.4 / 6.0 GB Free`).
- **Actions:**
  - `About / Info` icon (opens modal with project goals and license).
  - `Add Instance` icon button.

#### Content Area
1. **Resource Utilization Banner:**
   - Visual progress bar showing total allocated VM RAM vs. safe host RAM threshold.
   - Indicator text: "Host Memory Safe — Up to 3.0 GB allocatable for guests".
2. **Instance List (LazyColumn):**
   - Displays a list of created virtual instances.
   - Each **Instance Card** includes:
     - **Header:** Instance Name (e.g., `AOSP-14-ARM64`), Status Badge (`STOPPED`, `BOOTING`, `RUNNING`).
     - **Hardware Spec Matrix:**
       - `vCPUs`: e.g., `2 Cores`
       - `RAM`: e.g., `2048 MB`
       - `Storage`: e.g., `16 GB`
       - `OS Type`: e.g., `AOSP ARM64` / `Linux Generic`
     - **Metadata:** Last booted timestamp (e.g., `Last active: 2 hours ago` or `Never booted`).
     - **Action Footer:**
       - Primary `Start / Boot` button (Cyan accent, launches Screen 3).
       - Secondary `Edit` button (Opens edit mode in Screen 2).
       - `Delete` button (Shows confirmation dialog with instance destruction warning).
3. **Empty State:**
   - Rendered when zero instances exist in the Room database.
   - Icon: Large stylized mobile VM device outline.
   - Headline: "No Virtual Instances Created".
   - Subtext: "Create an isolated Android or Linux virtual instance to test applications, inspect kernels, or explore low-level systems programming."
   - Action Button: Prominent "Create First Instance" button.

#### Floating Action Button (FAB)
- Extended FAB: "+ New Instance" anchored at bottom right with smooth scroll-hide behavior.

---

### 2.2. Screen 2: Add / Edit Instance

#### Top App Bar
- Back navigation arrow with unsaved changes confirmation if form is dirty.
- Title: "New Virtual Instance" or "Edit Instance".

#### Form Sections
1. **Instance Identity Section:**
   - **Instance Name Input:** OutlinedTextField with character count, uniqueness validation against Room DB, and autofill suggestions (e.g., `AOSP-Dev-01`).
   - **OS Profile Preset Selector:** Segmented buttons:
     - `AOSP ARM64` (default 2GB RAM, 2 vCPUs, 16GB disk)
     - `Debian Linux` (default 1GB RAM, 1 vCPU, 8GB disk)
     - `Custom Raw` (manual entry)

2. **Virtual Hardware Allocation Section:**
   - **vCPU Allocation:** Slider from `1` to `4` cores with discrete tick marks and visual warning when exceeding 2 cores on lower-spec hosts.
   - **RAM Allocation:** Slider from `512 MB` to `4096 MB` in `256 MB` increments.
     - Color-coded safety zones: Green (`512MB - 2048MB`), Yellow (`2048MB - 3072MB`), Orange (`> 3072MB` - Memory pressure warning).
   - **Storage Disk Size:** Slider from `4 GB` to `64 GB` in `4 GB` steps with disk image format badge (`raw` / `qcow2`).

3. **Storage & Kernel Images (SAF Pickers):**
   - **Disk Image File:** Read-only path field with "Browse..." button triggering Storage Access Framework (SAF) document picker.
   - **Optional Kernel Binary:** Field for custom `Image` / `vmlinuz` kernel.
   - **Optional Initramfs:** Field for `initrd.img` ramdisk.

4. **Advanced Settings (Collapsible Accordion):**
   - **Extra Kernel Arguments:** Text input (e.g. `console=ttyAMA0 earlycon=pl011,0x09000000 root=/dev/vda rw init=/init`).
   - **Display Orientation:** Choice between `Portrait (9:16)` and `Landscape (16:9)`.
   - **Serial Console Logging:** Switch toggle to record Phase 2/3 UART output to file.

5. **Action Bar (Bottom Fixed):**
   - Secondary "Cancel" button.
   - Primary "Create Instance" / "Save Changes" button (disabled if form validation fails).

---

### 2.3. Screen 3: Boot Screen (Phase 1 Stub & Execution Preview)

#### Top Control Bar
- **Instance Identifier:** Title displaying active instance name and OS type.
- **State Pill:** Animated pulsating badge (`BOOTING` -> `RUNNING (STUB)`).
- **Execution Timer:** Stopwatch timer recording uptime (`00:01:24`).
- **Power Actions:**
  - `Power Off / ACPI Shutdown` button with graceful termination sequence.
  - `Reset` button.

#### Virtual Display Viewport (Primary Area)
- Centered container matching target aspect ratio (9:16 portrait or 16:9 landscape) bounded by a sleek hardware bezel outline.
- **Phase 1 Stub Graphic:**
  - High-tech simulated boot graphic with animated glowing scanline effect.
  - Informational overlay: "Phase 1 Interactive UI Stub — VM Backend connects in Phase 2/3".
  - Live virtual resolution readout (e.g., `720 x 1280 @ 60 Hz (virtio-gpu-pci)`).

#### Telemetry & Monospace Boot Console (Collapsible Bottom Sheet / Viewport)
- Monospace live log viewer streaming realistic ARM64 `virt` boot sequence:
  ```
  [    0.000000] Booting Linux on physical CPU 0x0000000000 [0x410fd034]
  [    0.000000] Linux version 6.6.0-virt-arm64 (buildroot@axilbox) (aarch64-linux-gnu-gcc)
  [    0.000000] Machine model: linux,dummy-virt
  [    0.000000] earlycon: pl011 at MMIO 0x0000000009000000 (options '')
  [    0.000000] Zone ranges: DMA32 [mem 0x0000000040000000-0x00000000bfffffff]
  [    0.012410] virtio-pci 0000:00:01.0: enabling device (0000 -> 0002)
  [    0.014200] virtio_blk virtio1: [vda] 33554432 512-byte logical blocks (17.1 GB/16.0 GiB)
  [    0.021000] virtio_gpu virtio2: features: +virgl +edid +resource_blob
  [    0.035120] init: [libprocessgroup] Created cgroup /sys/fs/cgroup/system
  [    0.048900] init: starting service 'surfaceflinger'...
  [    0.061000] SurfaceFlinger: using virtio-gpu DRM display (720x1280@60Hz)
  [    0.075000] init: Entering Android Userspace Stage 2
  ```
- Console toolbar: `Pause Stream`, `Clear Console`, `Copy Logs to Clipboard`.

#### Virtual Device Controls Toolbar
- `Rotate Screen` (swaps 9:16 <-> 16:9).
- `Virtual Keyboard Toggle` (stubs soft input invocation).
- `Screenshot Capture` (stubs frame buffer snapshot).
- `Fullscreen / Immersive Mode Toggle`.
