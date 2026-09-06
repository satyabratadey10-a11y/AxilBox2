#!/usr/bin/env bash
set -euo pipefail

OUTPUT_CPIO="${1:-rootfs-bundle/rootfs.cpio.gz}"
ORIGINAL_TAR_DIR="${2:-downloads/rootfs}"

mkdir -p "$(dirname "${OUTPUT_CPIO}")" "${ORIGINAL_TAR_DIR}"

ALPINE_VERSION="3.20.3"
ALPINE_TAR_NAME="alpine-minirootfs-${ALPINE_VERSION}-aarch64.tar.gz"
ALPINE_URL="https://dl-cdn.alpinelinux.org/alpine/v3.20/releases/aarch64/${ALPINE_TAR_NAME}"
ALPINE_SHA256="041fa34a81788242df9e78fa69b97ab45b8ec47ddbf88864755610414a7bf3de"

TAR_PATH="${ORIGINAL_TAR_DIR}/${ALPINE_TAR_NAME}"
SHA_PATH="${ORIGINAL_TAR_DIR}/${ALPINE_TAR_NAME}.sha256"

echo "=== AxilBox Alpine Minirootfs -> newc cpio.gz Converter ==="

# 1. Download original verified Alpine minirootfs .tar.gz if not present
if [ ! -f "${TAR_PATH}" ]; then
    echo "Downloading ${ALPINE_TAR_NAME} from official Alpine mirror..."
    curl -sSL --retry 3 "${ALPINE_URL}" -o "${TAR_PATH}"
fi

# 2. Assert SHA-256 checksum
echo "Verifying SHA-256 integrity..."
echo "${ALPINE_SHA256}  ${TAR_PATH}" | sha256sum -c - || {
    echo "FATAL: SHA-256 verification failed for ${TAR_PATH}!" >&2
    exit 1
}
echo "✓ SHA-256 verified (${ALPINE_SHA256})"

# Save sha256 file alongside original tar.gz for separate artifact archival
echo "${ALPINE_SHA256}  ${ALPINE_TAR_NAME}" > "${SHA_PATH}"

# 3. Extract to clean work directory
WORK_DIR=$(mktemp -d)
trap 'rm -rf "${WORK_DIR}"' EXIT

ROOTFS_DIR="${WORK_DIR}/rootfs"
mkdir -p "${ROOTFS_DIR}"

echo "Extracting minirootfs .tar.gz..."
tar -xzf "${TAR_PATH}" -C "${ROOTFS_DIR}"

# 4. Configure early userspace & serial console support for QEMU virt machine (PL011 UART / ttyAMA0)
echo "Configuring early userspace configuration..."

# Verify /sbin/init exists (Alpine puts BusyBox init at /sbin/init)
if [ ! -e "${ROOTFS_DIR}/sbin/init" ]; then
    echo "FATAL: /sbin/init not found in extracted minirootfs!" >&2
    exit 1
fi

# Ensure /etc/inittab attaches getty to ttyAMA0 for serial console interaction
if [ -f "${ROOTFS_DIR}/etc/inittab" ]; then
    if ! grep -q "ttyAMA0" "${ROOTFS_DIR}/etc/inittab"; then
        echo "ttyAMA0::respawn:/sbin/getty -L 115200 ttyAMA0 vt100" >> "${ROOTFS_DIR}/etc/inittab"
    fi
fi

# Provide early-userspace sysinit script for OpenRC sysinit in inittab
if [ ! -e "${ROOTFS_DIR}/sbin/openrc" ]; then
    cat << 'EOF' > "${ROOTFS_DIR}/sbin/openrc"
#!/bin/sh
echo "* [OpenRC] Starting early-userspace sysinit (AxilBox Guest Engine)..."
mount -t proc proc /proc 2>/dev/null || true
mount -t sysfs sys /sys 2>/dev/null || true
mount -t devtmpfs dev /dev 2>/dev/null || true
echo "* [OpenRC] System services initialized."
exit 0
EOF
    chmod +x "${ROOTFS_DIR}/sbin/openrc"
fi

# 5. Pack as newc format cpio and compress with gzip -9
echo "Repacking into newc format cpio.gz..."
(cd "${ROOTFS_DIR}" && find . | cpio -o -H newc) | gzip -9 > "${OUTPUT_CPIO}"

# 6. Verify derived cpio.gz artifact
if [ ! -f "${OUTPUT_CPIO}" ] || [ ! -s "${OUTPUT_CPIO}" ]; then
    echo "FATAL: Output cpio.gz artifact was not created or is empty!" >&2
    exit 1
fi

CPIO_SIZE=$(stat -c%s "${OUTPUT_CPIO}" 2>/dev/null || stat -f%z "${OUTPUT_CPIO}")
echo "✓ Derived cpio.gz created successfully: ${OUTPUT_CPIO} (${CPIO_SIZE} bytes)"

# Verify cpio contents contain /sbin/init
zcat "${OUTPUT_CPIO}" | cpio -t | grep -q "sbin/init" || {
    echo "FATAL: sbin/init missing from repacked cpio archive!" >&2
    exit 1
}
echo "✓ cpio archive integrity verified (contains sbin/init)"
