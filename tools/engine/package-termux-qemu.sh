#!/usr/bin/env bash
set -euo pipefail

# Output directory (defaults to jniLibs-bundle/arm64-v8a)
OUTPUT_DIR="${1:-jniLibs-bundle/arm64-v8a}"
mkdir -p "${OUTPUT_DIR}"
WORK_DIR=$(mktemp -d)
trap 'rm -rf "${WORK_DIR}"' EXIT

echo "=== Provisioning QEMU & Bionic Dependencies for Android jniLibs (Target: arm64-v8a) ==="

APT_REPO="https://packages.termux.dev/apt/termux-main"
PACKAGES_INDEX_URL="${APT_REPO}/dists/stable/main/binary-aarch64/Packages"

echo "[1/5] Fetching Termux aarch64 package index..."
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

echo "[2/5] Downloading and extracting required Bionic aarch64 packages..."
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

echo "[3/5] Locating QEMU executable and shared libraries..."

# Python helper to normalize names into lib*.so and patch ELF DT_NEEDED, DT_SONAME, and RPATH ($ORIGIN)
python3 - <<PY_SCRIPT
import os
import sys
import subprocess
import shutil
import re

work_dir = "${WORK_DIR}"
output_dir = "${OUTPUT_DIR}"
os.makedirs(output_dir, exist_ok=True)

# 1. Locate qemu-system-aarch64 executable
qemu_bin = None
for root, dirs, files in os.walk(work_dir):
    if "qemu-system-aarch64" in files:
        candidate = os.path.join(root, "qemu-system-aarch64")
        if os.path.isfile(candidate) and not os.path.islink(candidate):
            qemu_bin = candidate
            break

if not qemu_bin:
    print("ERROR: qemu-system-aarch64 binary not found in extracted packages!", file=sys.stderr)
    sys.exit(1)

qemu_target_so = os.path.join(output_dir, "libqemu_system_aarch64.so")
shutil.copy2(qemu_bin, qemu_target_so)
os.chmod(qemu_target_so, 0o755)
print(f" -> Copied QEMU binary to {qemu_target_so}")

# 2. Locate all .so and .so.* files
so_files = []
for root, dirs, files in os.walk(work_dir):
    for f in files:
        if ".so" in f:
            full_path = os.path.join(root, f)
            so_files.append(full_path)

# Map original filename (and SONAMEs) to clean canonical lib*.so
rename_map = {}

def get_canonical_name(filename):
    # e.g. libglib-2.0.so.0.7800.4 -> libglib-2.0.so
    # libpixman-1.so.0 -> libpixman-1.so
    # libz.so.1 -> libz.so
    # libffi.so.8 -> libffi.so
    base = os.path.basename(filename)
    canonical = re.sub(r'(\.so)(?:\.\d+)+$', r'\1', base)
    if not canonical.startswith("lib"):
        canonical = "lib" + canonical
    if not canonical.endswith(".so"):
        canonical = canonical + ".so"
    return canonical

for so_path in so_files:
    real_path = os.path.realpath(so_path)
    if not os.path.isfile(real_path):
        continue
    orig_name = os.path.basename(so_path)
    canon_name = get_canonical_name(orig_name)
    rename_map[orig_name] = canon_name
    
    target_path = os.path.join(output_dir, canon_name)
    if not os.path.exists(target_path) or os.path.getsize(target_path) < os.path.getsize(real_path):
        shutil.copy2(real_path, target_path)
        os.chmod(target_path, 0o755)

print(f"[4/5] Normalizing shared libraries to Android lib*.so convention:")
for old_n, new_n in sorted(rename_map.items()):
    print(f"   {old_n} -> {new_n}")

# 3. Patch ELF DT_SONAME, DT_NEEDED, and RPATH ($ORIGIN)
all_output_sos = [os.path.join(output_dir, f) for f in os.listdir(output_dir) if f.startswith("lib") and f.endswith(".so")]

def get_elf_needed(filepath):
    try:
        out = subprocess.check_output(["readelf", "-d", filepath], text=True, errors="ignore")
        needed = []
        for line in out.splitlines():
            if "(NEEDED)" in line:
                m = re.search(r'\[(.*?)\]', line)
                if m:
                    needed.append(m.group(1))
        return needed
    except Exception:
        return []

print("[5/5] Patching ELF RPATH to \$ORIGIN and updating DT_NEEDED entries...")
for so_path in all_output_sos:
    fname = os.path.basename(so_path)
    # Set soname for shared libraries (not the main qemu exec if it has none, but harmless if set)
    try:
        subprocess.run(["patchelf", "--set-soname", fname, so_path], check=False)
    except Exception:
        pass

    # Set RPATH to \$ORIGIN for co-located dynamic linking in nativeLibraryDir
    try:
        subprocess.run(["patchelf", "--set-rpath", "$ORIGIN", so_path], check=True)
    except Exception as e:
        print(f"Warning: Failed to set RPATH on {fname}: {e}", file=sys.stderr)

    # Patch NEEDED entries
    needed_libs = get_elf_needed(so_path)
    for needed in needed_libs:
        canon_needed = rename_map.get(needed, get_canonical_name(needed))
        if canon_needed != needed and os.path.exists(os.path.join(output_dir, canon_needed)):
            try:
                subprocess.run(["patchelf", "--replace-needed", needed, canon_needed, so_path], check=True)
                print(f"   [{fname}] Replaced NEEDED {needed} -> {canon_needed}")
            except Exception as e:
                print(f"Warning: Failed to replace needed {needed} in {fname}: {e}", file=sys.stderr)

print("=== jniLibs Packaging Complete ===")
PY_SCRIPT

echo "=== Verified Output Directory (${OUTPUT_DIR}) ==="
ls -lh "${OUTPUT_DIR}"

echo "=== Verifying ELF Headers and RPATH on all libraries ==="
for lib in "${OUTPUT_DIR}"/*.so; do
    echo "--- File: $(basename "${lib}") ---"
    readelf -h "${lib}" | grep "Class:\|Machine:" || true
    readelf -d "${lib}" | grep "SONAME\|RPATH\|RUNPATH\|NEEDED" || true
done
