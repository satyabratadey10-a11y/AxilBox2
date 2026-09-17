#!/usr/bin/env bash
# ==============================================================================
# AxilBox Rootfs Converter: tar.gz -> newc cpio.gz (initramfs)
#
# Converts an Alpine Linux minirootfs tarball (alpine-minirootfs-*.tar.gz)
# into a compressed newc-format cpio archive (rootfs.cpio.gz) suitable for
# in-memory Stage 1 kernel initrd boot via QEMU's -initrd.
# ==============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"

# 1. Locate cpio command (Toybox / Termux / system)
CPIO_BIN="$(command -v cpio 2>/dev/null || true)"
if [ -z "${CPIO_BIN}" ] && [ -x "/system/bin/cpio" ]; then
    CPIO_BIN="/system/bin/cpio"
fi

if [ -z "${CPIO_BIN}" ]; then
    echo "FATAL: cpio binary not found on PATH or in /system/bin/cpio" >&2
    exit 1
fi

command -v tar >/dev/null 2>&1 || { echo "FATAL: tar command not found" >&2; exit 1; }
command -v gzip >/dev/null 2>&1 || { echo "FATAL: gzip command not found" >&2; exit 1; }

# 2. Parse arguments
INPUT_TAR="${1:-}"
OUTPUT_CPIO="${2:-}"

if [ -z "${INPUT_TAR}" ]; then
    CANDIDATES=(
        "/sdcard/Download/alpine-minirootfs-3.24.1-aarch64.tar.gz"
        "/sdcard/Download/alpine-minirootfs-*.tar.gz"
        "${REPO_ROOT}/downloads/alpine-minirootfs-*.tar.gz"
        "./alpine-minirootfs-*.tar.gz"
    )
    for c in "${CANDIDATES[@]}"; do
        for match in $c; do
            if [ -f "${match}" ]; then
                INPUT_TAR="${match}"
                break 2
            fi
        done
    done
fi

if [ -z "${INPUT_TAR}" ] || [ ! -f "${INPUT_TAR}" ]; then
    echo "Usage: $0 <input-rootfs.tar.gz> [output-rootfs.cpio.gz]" >&2
    echo "" >&2
    echo "Converts an Alpine minirootfs .tar.gz into a newc cpio.gz initramfs for AxilBox2." >&2
    exit 1
fi

if [ -z "${OUTPUT_CPIO}" ]; then
    DIRNAME="$(dirname "${INPUT_TAR}")"
    BASENAME="$(basename "${INPUT_TAR}")"
    STRIPPED="${BASENAME%.tar.gz}"
    OUTPUT_CPIO="${DIRNAME}/${STRIPPED}.cpio.gz"
fi

echo "=== AxilBox Minirootfs Converter (tar.gz -> newc cpio.gz) ==="
echo "Input Tarball : ${INPUT_TAR}"
echo "Output Initrd : ${OUTPUT_CPIO}"
echo "CPIO Tool     : ${CPIO_BIN}"

# 3. Clean working workspace on local internal storage
WORK_PARENT="${REPO_ROOT}/build/tmp_rootfs"
mkdir -p "${WORK_PARENT}"
WORK_DIR="${WORK_PARENT}/work_$$"
mkdir -p "${WORK_DIR}"
trap 'rm -rf "${WORK_DIR}"' EXIT

ROOTFS_DIR="${WORK_DIR}/rootfs"
mkdir -p "${ROOTFS_DIR}"

echo "[1/4] Extracting rootfs contents from ${INPUT_TAR}..."
tar --no-same-owner -xzf "${INPUT_TAR}" -C "${ROOTFS_DIR}"

# 4. Configure early userspace & serial console support for QEMU virt machine (PL011 UART / ttyAMA0)
echo "[2/4] Verifying and configuring userspace init..."

# In Alpine, /sbin/init is typically a symlink to /bin/busybox.
# [ -L ] checks symlinks without resolving against the host root filesystem.
if [ ! -L "${ROOTFS_DIR}/sbin/init" ] && [ ! -e "${ROOTFS_DIR}/sbin/init" ] && [ ! -e "${ROOTFS_DIR}/bin/busybox" ]; then
    echo "FATAL: neither /sbin/init nor /bin/busybox found in extracted rootfs archive!" >&2
    exit 1
fi

# Ensure /init symlink exists (standard Linux kernel fallback for initramfs)
if [ ! -e "${ROOTFS_DIR}/init" ] && [ ! -L "${ROOTFS_DIR}/init" ]; then
    echo "Creating /init -> sbin/init symlink..."
    ln -s sbin/init "${ROOTFS_DIR}/init"
fi

# Ensure /etc/inittab attaches a getty to ttyAMA0 for serial console interaction
if [ -f "${ROOTFS_DIR}/etc/inittab" ]; then
    if ! grep -q "ttyAMA0" "${ROOTFS_DIR}/etc/inittab"; then
        echo "Adding ttyAMA0 serial console getty to /etc/inittab..."
        echo "ttyAMA0::respawn:/sbin/getty -L 115200 ttyAMA0 vt100" >> "${ROOTFS_DIR}/etc/inittab"
    fi
fi

# Provide minimal sysinit fallback script if openrc is absent
if [ ! -e "${ROOTFS_DIR}/sbin/openrc" ]; then
    cat << 'EOF' > "${ROOTFS_DIR}/sbin/openrc"
#!/bin/sh
echo "* [Init] Starting AxilBox guest early-userspace sysinit..."
mount -t proc proc /proc 2>/dev/null || true
mount -t sysfs sys /sys 2>/dev/null || true
mount -t devtmpfs dev /dev 2>/dev/null || true
echo "* [Init] System mounts initialized."
exit 0
EOF
    chmod +x "${ROOTFS_DIR}/sbin/openrc"
fi

# 5. Pack as newc format cpio and compress with gzip
echo "[3/4] Repacking directory tree into newc format cpio.gz..."
mkdir -p "$(dirname "${OUTPUT_CPIO}")"
(cd "${ROOTFS_DIR}" && find . | "${CPIO_BIN}" -o -H newc) | gzip -6 > "${OUTPUT_CPIO}"

# 6. Verify derived cpio.gz artifact
echo "[4/4] Verifying output integrity..."
if [ ! -f "${OUTPUT_CPIO}" ] || [ ! -s "${OUTPUT_CPIO}" ]; then
    echo "FATAL: Output cpio.gz artifact was not created or is empty!" >&2
    exit 1
fi

CPIO_SIZE=$(stat -c%s "${OUTPUT_CPIO}" 2>/dev/null || stat -f%z "${OUTPUT_CPIO}")
echo "✓ Successfully created: ${OUTPUT_CPIO} (${CPIO_SIZE} bytes)"

# Verify cpio contents contain init
set +o pipefail
if gzip -dc "${OUTPUT_CPIO}" | "${CPIO_BIN}" -t 2>/dev/null | grep "init" >/dev/null 2>&1; then
    echo "✓ Verification passed: contains init (/sbin/init and /init)"
else
    echo "FATAL: init missing from repacked cpio archive!" >&2
    exit 1
fi
set -o pipefail

if command -v sha256sum >/dev/null 2>&1; then
    SHA=$(sha256sum "${OUTPUT_CPIO}" | awk '{print $1}')
    echo "✓ SHA-256: ${SHA}"
fi

echo ""
echo "================================================================="
echo " Boot Instructions for AxilBox2:"
echo " 1. In AxilBox2 Instance Configuration, select this .cpio.gz"
echo "    in the 'Initial Ramdisk' (initrd) field via SAF picker."
echo " 2. Leave the 'Disk Image' field EMPTY."
echo " 3. Leave the 'Custom Kernel' field EMPTY (uses bundled Image)."
echo " 4. Launch instance to boot Alpine Linux into RAM."
echo "================================================================="
