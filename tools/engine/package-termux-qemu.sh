#!/usr/bin/env bash
set -euo pipefail

# Output directory (defaults to jniLibs-bundle/arm64-v8a)
OUTPUT_DIR="${1:-jniLibs-bundle/arm64-v8a}"
mkdir -p "${OUTPUT_DIR}"
WORK_DIR=$(mktemp -d)
trap 'rm -rf "${WORK_DIR}"' EXIT

echo "=== Provisioning QEMU & Recursive Bionic Dependencies for Android jniLibs (Target: arm64-v8a) ==="

APT_REPO="https://packages.termux.dev/apt/termux-main"
PACKAGES_INDEX_URL="${APT_REPO}/dists/stable/main/binary-aarch64/Packages"

echo "[1/4] Fetching Termux aarch64 package index..."
curl -sSL "${PACKAGES_INDEX_URL}" -o "${WORK_DIR}/Packages" || {
    echo "Warning: Primary mirror unreachable, fetching from backup mirror..."
    curl -sSL "https://raw.githubusercontent.com/termux/termux-packages/gh-pages/dists/stable/main/binary-aarch64/Packages" -o "${WORK_DIR}/Packages"
}

echo "[2/4] Resolving and downloading targeted recursive dependency closure..."

WORK_DIR="${WORK_DIR}" OUTPUT_DIR="${OUTPUT_DIR}" APT_REPO="${APT_REPO}" python3 - <<'PY_SCRIPT'
import os
import sys
import subprocess
import shutil
import re

work_dir = os.environ["WORK_DIR"]
output_dir = os.environ["OUTPUT_DIR"]
apt_repo = os.environ["APT_REPO"].rstrip("/")
packages_file = os.path.join(work_dir, "Packages")

os.makedirs(output_dir, exist_ok=True)

# Standard Android Bionic system libraries provided directly by Android OS (/system/lib64, /apex)
SYSTEM_LIBS = {
    "libc.so",
    "libm.so",
    "libdl.so",
    "liblog.so",
    "libandroid.so",
    "libaaudio.so",
    "libEGL.so",
    "libGLESv1_CM.so",
    "libGLESv2.so",
    "libGLESv3.so",
    "libvulkan.so",
    "libOpenSLES.so"
}

# 1. Parse Termux Packages index
print(" -> Parsing Termux Packages index...")
with open(packages_file, "r", encoding="utf-8", errors="ignore") as f:
    content = f.read()

packages = {}
blocks = content.split("\n\n")
for block in blocks:
    lines = block.splitlines()
    pkg_data = {}
    for line in lines:
        if ":" in line and not line.startswith(" "):
            k, v = line.split(":", 1)
            pkg_data[k.strip()] = v.strip()
    pkg_name = pkg_data.get("Package")
    if pkg_name:
        packages[pkg_name] = pkg_data

print(f" -> Indexed {len(packages)} packages from Termux repository.")

# Helper to extract Depends from package metadata
def get_package_depends(pkg_data):
    depends_str = pkg_data.get("Depends", "")
    if not depends_str:
        return []
    deps = []
    for item in depends_str.split(","):
        raw = item.strip()
        if not raw:
            continue
        dep_name = re.sub(r'\(.*?\)', '', raw).strip()
        if "|" in dep_name:
            dep_name = dep_name.split("|")[0].strip()
        if dep_name:
            deps.append(dep_name)
    return deps

downloaded_packages = set()

def download_and_extract_package(pkg_name):
    if pkg_name in downloaded_packages:
        return True
    
    pkg_data = packages.get(pkg_name)
    if not pkg_data:
        # Try finding by removing/adding 'lib' prefix
        alt = pkg_name[3:] if pkg_name.startswith("lib") else f"lib{pkg_name}"
        pkg_data = packages.get(alt)
        if pkg_data:
            pkg_name = alt

    if not pkg_data:
        # Search by case-insensitive match
        for p_name, p_data in packages.items():
            if pkg_name.lower() == p_name.lower():
                pkg_name = p_name
                pkg_data = p_data
                break

    if not pkg_data:
        print(f"   Warning: Package '{pkg_name}' not found in index.", file=sys.stderr)
        return False

    filename = pkg_data.get("Filename")
    if not filename:
        print(f"   Warning: No filename for package '{pkg_name}'.", file=sys.stderr)
        return False

    deb_url = f"{apt_repo}/{filename}"
    deb_path = os.path.join(work_dir, f"{pkg_name}.deb")
    extract_dir = os.path.join(work_dir, f"extracted_{pkg_name}")

    print(f"   Downloading: {pkg_name} ({filename})")
    try:
        subprocess.run(["curl", "-sSL", deb_url, "-o", deb_path], check=True)
        os.makedirs(extract_dir, exist_ok=True)
        subprocess.run(["dpkg-deb", "-x", deb_path, extract_dir], check=True)
        downloaded_packages.add(pkg_name)
        return True
    except Exception as e:
        print(f"   Error downloading/extracting {pkg_name}: {e}", file=sys.stderr)
        return False

def is_elf_file(filepath):
    if not os.path.isfile(filepath):
        return False
    try:
        with open(filepath, "rb") as f:
            magic = f.read(4)
            return magic == b"\x7fELF"
    except Exception:
        return False

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

def get_canonical_name(filename):
    base = os.path.basename(filename)
    canonical = re.sub(r'(\.so)(?:\.\d+)+$', r'\1', base)
    if not canonical.startswith("lib"):
        canonical = "lib" + canonical
    if not canonical.endswith(".so"):
        canonical = canonical + ".so"
    return canonical

# Library to package resolution mapping
KNOWN_LIB_MAP = {
    "gnutls": "gnutls",
    "nettle": "nettle",
    "hogweed": "nettle",
    "gmp": "libgmp",
    "tasn1": "libtasn1",
    "p11-kit": "p11-kit",
    "unistring": "libunistring",
    "idn2": "libidn2",
    "pixman": "libpixman",
    "pixman-1": "libpixman",
    "glib": "glib",
    "glib-2.0": "glib",
    "gmodule": "glib",
    "gmodule-2.0": "glib",
    "gobject": "glib",
    "gobject-2.0": "glib",
    "gio": "glib",
    "gio-2.0": "glib",
    "gthread": "glib",
    "gthread-2.0": "glib",
    "android-shmem": "libandroid-shmem",
    "iconv": "libiconv",
    "charset": "libiconv",
    "pcre2": "pcre2",
    "pcre2-8": "pcre2",
    "pcre2-16": "pcre2",
    "pcre2-32": "pcre2",
    "pcre2-posix": "pcre2",
    "ffi": "libffi",
    "z": "zlib",
    "c++": "libc++",
    "c++_shared": "libc++"
}

def resolve_package_for_lib(needed_so, canon_name):
    # Strip lib prefix and .so suffix
    stem = re.sub(r'^lib', '', needed_so)
    stem = re.sub(r'(\.so)(?:\.\d+)*$', '', stem)
    stem_no_num = re.sub(r'[-_]\d+.*$', '', stem)

    for lookup in [stem, stem_no_num, canon_name]:
        if lookup in KNOWN_LIB_MAP:
            return KNOWN_LIB_MAP[lookup]

    candidates = [stem, f"lib{stem}", stem_no_num, f"lib{stem_no_num}"]
    for cand in candidates:
        if cand in packages:
            return cand

    for p_name in packages:
        if stem.lower() == p_name.lower():
            return p_name

    return None

def find_elf_providing(needed_so, canon_name):
    for root, dirs, files in os.walk(work_dir):
        for f in files:
            if f == needed_so or f == canon_name or get_canonical_name(f) == canon_name:
                full_p = os.path.join(root, f)
                real_p = os.path.realpath(full_p)
                if is_elf_file(real_p):
                    return real_p
    return None

# 2. Download root package: qemu-system-aarch64-headless
print(" -> Provisioning root package: qemu-system-aarch64-headless...")
download_and_extract_package("qemu-system-aarch64-headless")

# Locate qemu-system-aarch64 executable
qemu_bin = None
for root, dirs, files in os.walk(work_dir):
    if "qemu-system-aarch64" in files:
        candidate = os.path.join(root, "qemu-system-aarch64")
        real_cand = os.path.realpath(candidate)
        if is_elf_file(real_cand):
            qemu_bin = real_cand
            break

if not qemu_bin:
    print("ERROR: qemu-system-aarch64 executable not found in extracted packages!", file=sys.stderr)
    sys.exit(1)

print(f" -> Found QEMU binary at {qemu_bin}")

# 3. Targeted Recursive Dependency Graph Resolution
print(" -> Running targeted recursive DT_NEEDED closure search from QEMU binary...")

needed_queue = list(get_elf_needed(qemu_bin))
processed_needed = set()
required_elf_paths = {} # canon_name -> real_path
rename_map = {} # old_needed_name -> canon_name

while needed_queue:
    needed_so = needed_queue.pop(0)
    if needed_so in processed_needed or needed_so in SYSTEM_LIBS:
        continue
    processed_needed.add(needed_so)

    canon_name = get_canonical_name(needed_so)
    rename_map[needed_so] = canon_name

    # Check if already present in work_dir
    found_elf = find_elf_providing(needed_so, canon_name)
    if not found_elf:
        pkg_name = resolve_package_for_lib(needed_so, canon_name)
        if pkg_name:
            print(f"   -> Resolving '{needed_so}' via package '{pkg_name}'")
            download_and_extract_package(pkg_name)
            found_elf = find_elf_providing(needed_so, canon_name)

    if found_elf:
        required_elf_paths[canon_name] = found_elf
        rename_map[os.path.basename(found_elf)] = canon_name
        # Add its DT_NEEDED entries to queue
        for sub_needed in get_elf_needed(found_elf):
            if sub_needed not in processed_needed and sub_needed not in SYSTEM_LIBS:
                needed_queue.append(sub_needed)
    else:
        print(f"ERROR: Could not resolve required dependency '{needed_so}' ({canon_name})!", file=sys.stderr)
        sys.exit(1)

print(f" -> Successfully resolved {len(required_elf_paths)} transitive shared libraries for QEMU.")

# 4. Assemble into OUTPUT_DIR
print("[3/4] Assembling and normalizing native libraries into output directory...")

# Copy QEMU binary as libqemu_system_aarch64.so
qemu_target_so = os.path.join(output_dir, "libqemu_system_aarch64.so")
shutil.copy2(qemu_bin, qemu_target_so)
os.chmod(qemu_target_so, 0o755)
print(f" -> Copied QEMU binary -> {qemu_target_so}")

# Copy all required shared libraries
for canon_name, src_path in required_elf_paths.items():
    dst_path = os.path.join(output_dir, canon_name)
    shutil.copy2(src_path, dst_path)
    os.chmod(dst_path, 0o755)
    print(f"    + {canon_name} (from {os.path.basename(src_path)})")

# 5. Patch ELF DT_SONAME, DT_NEEDED, and RPATH ($ORIGIN)
print("[4/4] Patching ELF RPATH to $ORIGIN and remapping DT_NEEDED entries...")
all_output_sos = [os.path.join(output_dir, f) for f in os.listdir(output_dir) if f.startswith("lib") and f.endswith(".so")]

for so_path in all_output_sos:
    fname = os.path.basename(so_path)
    try:
        subprocess.run(["patchelf", "--set-soname", fname, so_path], check=False)
    except Exception:
        pass

    try:
        subprocess.run(["patchelf", "--set-rpath", "$ORIGIN", so_path], check=True)
    except Exception as e:
        print(f"Warning: Failed to set RPATH on {fname}: {e}", file=sys.stderr)

    needed_libs = get_elf_needed(so_path)
    for needed in needed_libs:
        canon_needed = rename_map.get(needed, get_canonical_name(needed))
        if canon_needed != needed and os.path.exists(os.path.join(output_dir, canon_needed)):
            try:
                subprocess.run(["patchelf", "--replace-needed", needed, canon_needed, so_path], check=True)
            except Exception as e:
                print(f"Warning: Failed to replace needed {needed} in {fname}: {e}", file=sys.stderr)

# 6. Strict completeness validation
print("=== Strict DT_NEEDED Completeness Verification ===")
missing_total = 0
available_bundle_sos = set(os.listdir(output_dir))

for fname in sorted(available_bundle_sos):
    if not (fname.startswith("lib") and fname.endswith(".so")):
        continue
    fpath = os.path.join(output_dir, fname)
    for needed in get_elf_needed(fpath):
        if needed in SYSTEM_LIBS:
            continue
        if needed not in available_bundle_sos:
            print(f"ERROR: [{fname}] requires '{needed}', which is MISSING from bundle!", file=sys.stderr)
            missing_total += 1

if missing_total > 0:
    print(f"FATAL: {missing_total} unsatisfied transitive dependencies detected!", file=sys.stderr)
    sys.exit(1)

print(f"✓ All {len(available_bundle_sos)} native libraries are 100% self-contained and satisfy all DT_NEEDED dependencies!")
PY_SCRIPT

echo "=== Verified Output Directory (${OUTPUT_DIR}) ==="
ls -lh "${OUTPUT_DIR}"
