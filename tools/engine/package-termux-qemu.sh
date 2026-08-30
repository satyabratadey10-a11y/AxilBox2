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

echo "[2/4] Resolving and downloading complete recursive dependency closure..."

WORK_DIR="${WORK_DIR}" OUTPUT_DIR="${OUTPUT_DIR}" APT_REPO="${APT_REPO}" python3 - <<'PY_SCRIPT'
import os
import sys
import subprocess
import shutil
import re
import urllib.request

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
    "liblog.so"
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
        # Fuzzy match
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

# Initial seed packages for QEMU aarch64 and core runtime
seed_pkgs = [
    "qemu-system-aarch64-headless",
    "glib",
    "libpixman",
    "libandroid-shmem",
    "libiconv",
    "pcre2",
    "libffi",
    "zlib",
    "gnutls",
    "nettle",
    "libgmp",
    "libtasn1",
    "p11-kit",
    "libunistring",
    "libidn2",
    "libc++"
]

print(" -> Resolving initial package dependencies tree...")
queue = list(seed_pkgs)
while queue:
    current = queue.pop(0)
    if current in downloaded_packages:
        continue
    success = download_and_extract_package(current)
    if success:
        pkg_data = packages.get(current, {})
        for dep in get_package_depends(pkg_data):
            if dep not in downloaded_packages and dep not in queue:
                queue.append(dep)

# Helper functions for ELF examination
def is_elf_file(filepath):
    if not os.path.isfile(filepath) or os.path.islink(filepath):
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

# 2. Recursive ELF DT_NEEDED closure loop
print(" -> Running recursive ELF DT_NEEDED closure analysis...")
iteration = 0
while True:
    iteration += 1
    # Find all ELF binaries and shared libraries in work_dir
    all_elfs = []
    available_sos = {} # name -> path
    for root, dirs, files in os.walk(work_dir):
        for f in files:
            full_path = os.path.join(root, f)
            if is_elf_file(full_path):
                all_elfs.append(full_path)
                bname = os.path.basename(full_path)
                available_sos[bname] = full_path
                canon = get_canonical_name(bname)
                available_sos[canon] = full_path

    # Collect all DT_NEEDED entries
    all_needed = set()
    for elf in all_elfs:
        for needed in get_elf_needed(elf):
            all_needed.add(needed)

    # Find missing dependencies
    missing = set()
    for needed in all_needed:
        if needed in SYSTEM_LIBS:
            continue
        canon_needed = get_canonical_name(needed)
        if needed not in available_sos and canon_needed not in available_sos:
            missing.add(needed)

    if not missing:
        print(f" -> All ELF DT_NEEDED dependencies satisfied after {iteration} iteration(s)!")
        break

    print(f" -> Iteration {iteration}: Found {len(missing)} unresolved DT_NEEDED dependencies: {sorted(missing)}")
    new_download_count = 0

    for miss in missing:
        # Derive package search stem (e.g. libgnutls.so.30 -> gnutls, libnettle.so.8 -> nettle)
        stem = re.sub(r'^lib', '', miss)
        stem = re.sub(r'(\.so)(?:\.\d+)*$', '', stem)
        candidates = [stem, f"lib{stem}"]

        matched_pkg = None
        for cand in candidates:
            if cand in packages:
                matched_pkg = cand
                break

        if not matched_pkg:
            for p_name, p_data in packages.items():
                if stem.lower() == p_name.lower() or f"lib{stem.lower()}" == p_name.lower():
                    matched_pkg = p_name
                    break

        if not matched_pkg:
            for p_name, p_data in packages.items():
                if stem.lower() in p_name.lower():
                    matched_pkg = p_name
                    break

        if matched_pkg and matched_pkg not in downloaded_packages:
            print(f"    -> Resolving missing dependency '{miss}' via package '{matched_pkg}'")
            if download_and_extract_package(matched_pkg):
                new_download_count += 1

    if new_download_count == 0:
        print(f"Warning: Could not automatically resolve remaining missing dependencies: {missing}", file=sys.stderr)
        break

# 3. Assemble and normalize into OUTPUT_DIR (lib*.so)
print("[3/4] Assembling and normalizing native libraries into output directory...")

# Find QEMU executable
qemu_bin = None
for root, dirs, files in os.walk(work_dir):
    if "qemu-system-aarch64" in files:
        candidate = os.path.join(root, "qemu-system-aarch64")
        if is_elf_file(candidate):
            qemu_bin = candidate
            break

if not qemu_bin:
    print("ERROR: qemu-system-aarch64 binary not found in extracted packages!", file=sys.stderr)
    sys.exit(1)

qemu_target_so = os.path.join(output_dir, "libqemu_system_aarch64.so")
shutil.copy2(qemu_bin, qemu_target_so)
os.chmod(qemu_target_so, 0o755)
print(f" -> Copied QEMU binary -> {qemu_target_so}")

# Locate and copy all .so files
rename_map = {}
for root, dirs, files in os.walk(work_dir):
    for f in files:
        if ".so" in f:
            full_path = os.path.join(root, f)
            real_path = os.path.realpath(full_path)
            if not is_elf_file(real_path):
                continue
            
            orig_name = os.path.basename(full_path)
            canon_name = get_canonical_name(orig_name)
            rename_map[orig_name] = canon_name
            rename_map[os.path.basename(real_path)] = canon_name

            target_path = os.path.join(output_dir, canon_name)
            if not os.path.exists(target_path) or os.path.getsize(target_path) < os.path.getsize(real_path):
                shutil.copy2(real_path, target_path)
                os.chmod(target_path, 0o755)

print(f" -> Total bundled shared libraries: {len(os.listdir(output_dir))}")

# 4. Patch ELF DT_SONAME, DT_NEEDED, and RPATH ($ORIGIN)
print("[4/4] Patching ELF RPATH to $ORIGIN and remapping DT_NEEDED entries...")
all_output_sos = [os.path.join(output_dir, f) for f in os.listdir(output_dir) if f.startswith("lib") and f.endswith(".so")]

for so_path in all_output_sos:
    fname = os.path.basename(so_path)
    # Set soname
    try:
        subprocess.run(["patchelf", "--set-soname", fname, so_path], check=False)
    except Exception:
        pass

    # Set RPATH to $ORIGIN
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
            except Exception as e:
                print(f"Warning: Failed to replace needed {needed} in {fname}: {e}", file=sys.stderr)

# 5. Strict completeness validation
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
