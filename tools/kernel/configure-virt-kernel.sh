#!/usr/bin/env bash
set -euo pipefail

# AxilBox Stage 1 Minimal virt Machine Kernel Configuration
# Pinned for QEMU 'virt' machine per PLAN.md Stage 1 & RESEARCHDATA.md

# Serial & Earlycon Console
./scripts/config --enable CONFIG_SERIAL_AMBA_PL011
./scripts/config --enable CONFIG_SERIAL_AMBA_PL011_CONSOLE
./scripts/config --enable CONFIG_SERIAL_EARLYCON
./scripts/config --disable CONFIG_SERIAL_EARLYCON_ARM_SEMIHOST

# VirtIO Core & Transport
./scripts/config --enable CONFIG_VIRTIO
./scripts/config --enable CONFIG_VIRTIO_PCI
./scripts/config --enable CONFIG_VIRTIO_PCI_MODERN
./scripts/config --enable CONFIG_VIRTIO_MMIO
./scripts/config --enable CONFIG_VIRTIO_MMIO_CMDLINE_DEVICES

# VirtIO Storage
./scripts/config --enable CONFIG_VIRTIO_BLK
./scripts/config --enable CONFIG_EXT4_FS
./scripts/config --enable CONFIG_EXT4_FS_POSIX_ACL
./scripts/config --enable CONFIG_EXT4_FS_SECURITY

# Core Hardware & Addressing
./scripts/config --enable CONFIG_ARM64_VA_BITS_48
./scripts/config --enable CONFIG_ARM64_4K_PAGES
./scripts/config --enable CONFIG_ARM64_GIC_V3
./scripts/config --enable CONFIG_ARM64_GIC_V3_ITS
./scripts/config --enable CONFIG_PCI
./scripts/config --enable CONFIG_PCI_HOST_GENERIC
./scripts/config --enable CONFIG_PCI_ECAM

# Logging & TTY
./scripts/config --enable CONFIG_PRINTK
./scripts/config --enable CONFIG_PRINTK_TIME
./scripts/config --enable CONFIG_TTY

# Stage 1 Minimal: Disable virtio-gpu and virtio-input to keep scoped
./scripts/config --disable CONFIG_DRM_VIRTIO_GPU
./scripts/config --disable CONFIG_VIRTIO_INPUT
./scripts/config --disable CONFIG_INPUT_EVDEV
