#!/usr/bin/env bash
set -euo pipefail

# Output directory
OUTPUT_DIR="${1:-qemu-bundle}"
mkdir -p "${OUTPUT_DIR}/lib"
WORK_DIR=$(mktemp -d)
trap 'rm -rf "${WORK_DIR}"' EXIT

echo "=== Provisioning QEMU & Bionic Dependencies (Termux Recipes Target: Android aarch64) ==="

APT_REPO="https://packages.termux.dev/apt/termux-main"
PACKAGES_INDEX_URL="${APT_REPO}/dists/stable/main/binary-aarch64/Packages"

echo "[1/4] Fetching Termux aarch64 package index..."
curl -sSL "${PACKAGES_INDEX_URL}" -o "${WORK_DIR}/Packages" || {
    echo "Warning: Primary mirror unreachable, fetching from backup mirror..."
    curl -sSL "https://raw.githubusercontent.com/termux/termux-packages/gh-pages/dists/stable/main/binary-aarch64/Packages" -o "${WORK_DIR}/Packages"
}

# Helper to find package filename by exact or alternate name
find_deb_url() {
    local target="$1"
    python3 -c "
import sys

target = sys.argv[1]
with open('${WORK_DIR}/Packages', 'r', encoding='utf-8', errors='ignore') as f:
    content = f.read()

blocks = content.split('\n\n')
for block in blocks:
    lines = block.splitlines()
    pkg_name = ''
    filename = ''
    for line in lines:
        if line.startswith('Package: '):
            pkg_name = line.split('Package: ', 1)[1].strip()
        elif line.startswith('Filename: '):
            filename = line.split('Filename: ', 1)[1].strip()
    if pkg_name.lower() == target.lower():
        print(filename)
        sys.exit(0)

# Prefix / fallback match
for block in blocks:
    lines = block.splitlines()
    pkg_name = ''
    filename = ''
    for line in lines:
        if line.startswith('Package: '):
            pkg_name = line.split('Package: ', 1)[1].strip()
        elif line.startswith('Filename: '):
            filename = line.split('Filename: ', 1)[1].strip()
    if target.lower() in pkg_name.lower():
        print(filename)
        sys.exit(0)
" "$target"
}

PKGS=(
    "qemu-system-aarch64-headless"
    "glib"
    "libpixman"
    "libandroid-shmem"
    "libiconv"
    "pcre2"
    "libffi"
    "zlib"
)

echo "[2/4] Downloading and extracting required Bionic aarch64 packages..."
for pkg in "${PKGS[@]}"; do
    FILENAME=$(find_deb_url "${pkg}")
    if [[ -z "${FILENAME}" ]]; then
        echo "Trying alternate name without 'lib' prefix..."
        ALT_NAME="${pkg#lib}"
        FILENAME=$(find_deb_url "${ALT_NAME}")
    fi
    
    if [[ -n "${FILENAME}" ]]; then
        PKG_URL="${APT_REPO}/${FILENAME}"
        echo " -> Downloading ${pkg} from ${PKG_URL}"
        curl -sSL "${PKG_URL}" -o "${WORK_DIR}/${pkg}.deb"
        dpkg-deb -x "${WORK_DIR}/${pkg}.deb" "${WORK_DIR}/extracted_${pkg}"
    else
        echo "Warning: Package ${pkg} not found in index, continuing..."
    fi
done

echo "[3/4] Assembling standalone self-contained bundle..."

# Locate and copy qemu-system-aarch64 binary
QEMU_BIN=$(find "${WORK_DIR}" -name "qemu-system-aarch64" -type f | head -n 1)
if [[ -z "${QEMU_BIN}" ]]; then
    echo "ERROR: qemu-system-aarch64 binary not found in extracted packages!"
    exit 1
fi
cp "${QEMU_BIN}" "${OUTPUT_DIR}/qemu-system-aarch64"
chmod 755 "${OUTPUT_DIR}/qemu-system-aarch64"

# Locate and copy all needed shared libraries
find "${WORK_DIR}" -name "*.so*" -type f -exec cp -d {} "${OUTPUT_DIR}/lib/" \; || true
find "${WORK_DIR}" -name "*.so*" -type l -exec cp -d {} "${OUTPUT_DIR}/lib/" \; || true

# Check patchelf availability
if command -v patchelf >/dev/null 2>&1; then
    echo "[4/4] Patching ELF RPATH to \$ORIGIN:\$ORIGIN/lib:\$ORIGIN/../lib..."
    patchelf --set-rpath '$ORIGIN:$ORIGIN/lib:$ORIGIN/../lib' "${OUTPUT_DIR}/qemu-system-aarch64" || true
    for so in "${OUTPUT_DIR}/lib"/*.so*; do
        if [[ -f "${so}" && ! -L "${so}" ]]; then
            patchelf --set-rpath '$ORIGIN:$ORIGIN/lib:$ORIGIN/../lib' "${so}" || true
        fi
    done
fi

echo "=== Bundle Assembly Complete ==="
echo "Binary: ${OUTPUT_DIR}/qemu-system-aarch64"
ls -lh "${OUTPUT_DIR}/qemu-system-aarch64"
echo "Shared libraries in ${OUTPUT_DIR}/lib:"
ls -lh "${OUTPUT_DIR}/lib"
