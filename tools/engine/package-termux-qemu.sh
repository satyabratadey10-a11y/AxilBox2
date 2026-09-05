#!/usr/bin/env bash
set -euo pipefail

# Output directory (defaults to jniLibs-bundle/arm64-v8a)
OUTPUT_DIR="${1:-jniLibs-bundle/arm64-v8a}"
ASSETS_DIR="${2:-assets-bundle/engine/pc-bios}"
mkdir -p "${OUTPUT_DIR}" "${ASSETS_DIR}"
WORK_DIR=$(mktemp -d)
trap 'rm -rf "${WORK_DIR}"' EXIT

echo "=== Provisioning QEMU, Recursive Bionic Dependencies & pc-bios Assets ==="

APT_REPO="https://packages.termux.dev/apt/termux-main"
PACKAGES_INDEX_URL="${APT_REPO}/dists/stable/main/binary-aarch64/Packages"

echo "[1/4] Fetching Termux aarch64 package index..."
curl -sSL "${PACKAGES_INDEX_URL}" -o "${WORK_DIR}/Packages" || {
    echo "Warning: Primary mirror unreachable, fetching from backup mirror..."
    curl -sSL "https://raw.githubusercontent.com/termux/termux-packages/gh-pages/dists/stable/main/binary-aarch64/Packages" -o "${WORK_DIR}/Packages"
}

echo "[2/4] Resolving and downloading targeted recursive dependency closure..."

WORK_DIR="${WORK_DIR}" OUTPUT_DIR="${OUTPUT_DIR}" ASSETS_DIR="${ASSETS_DIR}" APT_REPO="${APT_REPO}" python3 - <<'PY_SCRIPT'
import os
import sys
import subprocess
import shutil
import re

work_dir = os.environ["WORK_DIR"]
output_dir = os.environ["OUTPUT_DIR"]
assets_dir = os.environ.get("ASSETS_DIR", "assets-bundle/engine/pc-bios")
apt_repo = os.environ["APT_REPO"].rstrip("/")
packages_file = os.path.join(work_dir, "Packages")

os.makedirs(output_dir, exist_ok=True)
os.makedirs(assets_dir, exist_ok=True)

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
        alt = pkg_name[3:] if pkg_name.startswith("lib") else f"lib{pkg_name}"
        pkg_data = packages.get(alt)
        if pkg_data:
            pkg_name = alt

    if not pkg_data:
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

def is_real_elf_shared_object(filepath, allow_executable=False):
    if not os.path.isfile(filepath):
        return False
    # Reject near-zero-byte stubs
    try:
        if os.path.getsize(filepath) < 1024:
            return False
    except Exception:
        return False

    # Check 64-bit ELF magic bytes (EI_CLASS == 2 for ELFCLASS64)
    try:
        with open(filepath, "rb") as f:
            magic = f.read(5)
            if len(magic) < 5 or magic[:4] != b"\x7fELF" or magic[4] != 2:
                return False
    except Exception:
        return False

    # Validate using file -b to ensure not ASCII text or build linker script
    try:
        out = subprocess.check_output(["file", "-b", filepath], text=True, errors="ignore").strip()
        if "ASCII text" in out or "linker script" in out:
            return False
        if "ELF 64-bit" not in out:
            return False
        if allow_executable:
            if "shared object" not in out and "executable" not in out:
                return False
        else:
            if "shared object" not in out:
                return False
    except Exception:
        return False

    return True

def is_elf_file(filepath):
    return is_real_elf_shared_object(filepath, allow_executable=True)

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

def get_elf_soname(filepath):
    try:
        out = subprocess.check_output(["readelf", "-d", filepath], text=True, errors="ignore")
        for line in out.splitlines():
            if "(SONAME)" in line:
                m = re.search(r'\[(.*?)\]', line)
                if m:
                    return m.group(1)
    except Exception:
        pass
    return None

def get_canonical_name(filename):
    base = os.path.basename(filename)
    canonical = re.sub(r'(\.so)(?:\.\d+)+$', r'\1', base)
    if not canonical.startswith("lib"):
        canonical = "lib" + canonical
    if not canonical.endswith(".so"):
        canonical = canonical + ".so"
    return canonical

# Mapping of library stems to official Termux package names
KNOWN_LIB_MAP = {
    "fdt": "dtc",
    "gnutls": "libgnutls",
    "nettle": "libnettle",
    "hogweed": "libnettle",
    "gmp": "libgmp",
    "tasn1": "libtasn1",
    "p11-kit": "p11-kit",
    "unbound": "libunbound",
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
    "png": "libpng",
    "png16": "libpng",
    "jpeg": "libjpeg-turbo",
    "jpeg-turbo": "libjpeg-turbo",
    "bz2": "libbz2",
    "lzma": "liblzma",
    "lzo": "liblzo",
    "lzo2": "liblzo",
    "zstd": "zstd",
    "xml2": "libxml2",
    "slirp": "libslirp",
    "brotli": "brotli",
    "brotlicommon": "brotli",
    "brotlidec": "brotli",
    "brotlienc": "brotli",
    "c++": "libc++",
    "c++_shared": "libc++",
    "android-support": "libandroid-support",
    "event": "libevent",
    "nghttp2": "libnghttp2",
    "ngtcp2": "libngtcp2",
    "ssl": "openssl",
    "crypto": "openssl",
    "curl": "libcurl",
    "ssh": "libssh",
    "cap-ng": "libcap-ng",
    "fuse3": "libfuse3",
    "vdeplug": "libvdeplug",
    "nfs": "libnfs",
    "usb": "libusb",
    "usb-1.0": "libusb",
    "usbredir": "libusbredir",
    "usbredirparser": "libusbredir",
    "spice": "libspice-server",
    "spice-server": "libspice-server",
    "pulse": "pulseaudio",
    "pulse-simple": "pulseaudio",
    "asound": "alsa-lib",
    "dw": "libdw",
    "elf": "libelf",
    "ncurses": "ncurses",
    "ncursesw": "ncurses"
}

def resolve_package_for_lib(needed_so, canon_name):
    stem = re.sub(r'^lib', '', needed_so)
    stem = re.sub(r'(\.so)(?:\.\d+)*$', '', stem)
    stem_no_num = re.sub(r'[-_]?\d+.*$', '', stem)

    for lookup in [needed_so, canon_name, stem, stem_no_num]:
        if lookup in KNOWN_LIB_MAP:
            return KNOWN_LIB_MAP[lookup]

    for cand in [f"lib{stem}", stem, f"lib{stem_no_num}", stem_no_num]:
        if cand in packages:
            return cand

    for p_name in packages:
        if stem.lower() == p_name.lower() or f"lib{stem.lower()}" == p_name.lower():
            return p_name
        if stem_no_num and (stem_no_num.lower() == p_name.lower() or f"lib{stem_no_num.lower()}" == p_name.lower()):
            return p_name

    for p_name in packages:
        if stem.lower() in p_name.lower():
            return p_name

    return None

def find_elf_providing(needed_so, canon_name):
    candidates = []
    for root, dirs, files in os.walk(work_dir):
        for f in files:
            full_p = os.path.join(root, f)
            real_p = os.path.realpath(full_p)
            if not is_real_elf_shared_object(real_p, allow_executable=False):
                continue

            bname = os.path.basename(full_p)
            real_bname = os.path.basename(real_p)
            cname = get_canonical_name(bname)
            soname = get_elf_soname(real_p)

            # Priority 1: Exact match on needed_so (e.g. libzstd.so.1)
            if bname == needed_so or real_bname == needed_so or (soname and soname == needed_so):
                candidates.append((1, real_p))
            # Priority 2: Versioned runtime binary (e.g. soname matches canon or versioned filename)
            elif (soname and get_canonical_name(soname) == canon_name) or real_bname.startswith(f"{canon_name}."):
                candidates.append((2, real_p))
            # Priority 3: General canon_name match (only if genuine ELF shared object)
            elif bname == canon_name or cname == canon_name or bname.startswith(canon_name):
                candidates.append((3, real_p))

    if candidates:
        # Sort by priority (lowest number first) then by file size descending (prefer full runtime library over stubs)
        candidates.sort(key=lambda x: (x[0], -os.path.getsize(x[1])))
        return candidates[0][1]

    return None

pkg_queue = [
    "qemu-system-aarch64-headless",
    "qemu-common",
    "glib", "libpixman", "libandroid-shmem", "libiconv", "pcre2", "libffi", "zlib",
    "libgnutls", "libnettle", "libgmp", "libtasn1", "p11-kit", "libunistring", "libidn2",
    "libc++", "dtc", "libpng", "libjpeg-turbo", "liblzo", "libbz2", "zstd", "libslirp"
]

while pkg_queue:
    pkg_name = pkg_queue.pop(0)
    if pkg_name in downloaded_packages:
        continue
    if download_and_extract_package(pkg_name):
        pkg_data = packages.get(pkg_name, {})
        for dep in get_package_depends(pkg_data):
            if dep not in downloaded_packages and dep not in pkg_queue:
                pkg_queue.append(dep)

# Locate qemu-system-aarch64 executable
qemu_bin = None
for root, dirs, files in os.walk(work_dir):
    if "qemu-system-aarch64" in files:
        candidate = os.path.join(root, "qemu-system-aarch64")
        if not os.path.islink(candidate) and is_elf_file(candidate):
            qemu_bin = candidate
            break

if not qemu_bin:
    print("ERROR: qemu-system-aarch64 executable not found in extracted packages!", file=sys.stderr)
    sys.exit(1)

print(f" -> Found QEMU binary at {qemu_bin}")

# 3. Targeted Recursive ELF DT_NEEDED Resolution
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
        soname = get_elf_soname(found_elf)
        if soname:
            rename_map[soname] = canon_name

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

# 6. Strict completeness and symbol-level closure validation
print("=== Strict Package Completeness & ELF Format Audit ===")

# Audit every file in output_dir using file and size checks
invalid_bundle_files = []
for fname in sorted(os.listdir(output_dir)):
    fpath = os.path.join(output_dir, fname)
    size = os.path.getsize(fpath)
    file_desc = subprocess.check_output(["file", "-b", fpath], text=True, errors="ignore").strip()
    print(f"  * [{fname}] ({size} bytes): {file_desc}")

    if size < 1024:
        invalid_bundle_files.append((fname, f"near-zero-byte stub ({size} bytes)", file_desc))
    elif "ASCII text" in file_desc or "linker script" in file_desc:
        invalid_bundle_files.append((fname, "ASCII text / linker script instead of ELF binary", file_desc))
    elif "ELF 64-bit" not in file_desc:
        invalid_bundle_files.append((fname, "not ELF 64-bit", file_desc))
    elif "shared object" not in file_desc and "executable" not in file_desc:
        invalid_bundle_files.append((fname, "neither shared object nor executable", file_desc))

if invalid_bundle_files:
    print(f"FATAL: {len(invalid_bundle_files)} packaged libraries failed ELF audit:", file=sys.stderr)
    for fname, reason, desc in invalid_bundle_files:
        print(f"  - {fname}: {reason} ('{desc}')", file=sys.stderr)
    sys.exit(1)

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

print("=== Strict Dynamic Symbol Export Verification ===")
readelf_bin = "aarch64-linux-gnu-readelf" if subprocess.run(["which", "aarch64-linux-gnu-readelf"], stdout=subprocess.DEVNULL).returncode == 0 else "readelf"

empty_dynsyms = []
for fname in sorted(available_bundle_sos):
    if fname == "libqemu_system_aarch64.so":
        continue
    fpath = os.path.join(output_dir, fname)
    try:
        out = subprocess.check_output([readelf_bin, "-W", "--dyn-syms", fpath], text=True, errors="ignore")
        has_exports = False
        for line in out.splitlines():
            parts = line.strip().split()
            if len(parts) >= 8 and parts[4] in ("GLOBAL", "WEAK") and parts[5] == "DEFAULT" and parts[6] not in ("UND", "UNDEF") and not parts[6].startswith("UND"):
                has_exports = True
                break
        if not has_exports:
            empty_dynsyms.append(fname)
    except Exception as e:
        print(f"Warning inspecting dynsyms on {fname}: {e}", file=sys.stderr)

if empty_dynsyms:
    print(f"FATAL: The following libraries have empty dynamic symbol export tables (stubs): {empty_dynsyms}", file=sys.stderr)
    sys.exit(1)

print(f"✓ All {len(available_bundle_sos)} native libraries are 100% self-contained, ELF-audited, and functionally complete!")

# 7. Locate, bundle, and audit QEMU pc-bios Option-ROMs / firmware
print("=== Locating and Bundling QEMU pc-bios Option-ROMs / Firmware ===")
pc_bios_src = None

# Search for efi-virtio.rom or share/qemu or pc-bios in extracted packages
for root, dirs, files in os.walk(work_dir):
    if "efi-virtio.rom" in files:
        pc_bios_src = root
        break

if not pc_bios_src:
    for root, dirs, files in os.walk(work_dir):
        if (root.endswith("share/qemu") or root.endswith("pc-bios")) and any(f.endswith((".rom", ".bin", ".fd")) for f in files):
            pc_bios_src = root
            break

if not pc_bios_src:
    print("FATAL: Could not locate QEMU pc-bios firmware directory in extracted packages!", file=sys.stderr)
    sys.exit(1)

print(f" -> Found QEMU pc-bios source at: {pc_bios_src}")

rom_count = 0
for item in sorted(os.listdir(pc_bios_src)):
    s = os.path.join(pc_bios_src, item)
    d = os.path.join(assets_dir, item)
    if os.path.isdir(s):
        shutil.copytree(s, d, dirs_exist_ok=True)
    else:
        shutil.copy2(s, d)
    rom_count += 1

print(f" -> Successfully bundled {rom_count} firmware/ROM entries into {assets_dir}")

# Audit required ROM files
required_roms = ["efi-virtio.rom", "efi-e1000.rom"]
missing_roms = []
for rom in required_roms:
    p = os.path.join(assets_dir, rom)
    if not os.path.exists(p):
        missing_roms.append((rom, "file not found"))
    elif os.path.getsize(p) < 1024:
        missing_roms.append((rom, f"file too small ({os.path.getsize(p)} bytes)"))

if missing_roms:
    print(f"FATAL: Missing required pc-bios romfiles in {assets_dir}: {missing_roms}", file=sys.stderr)
    sys.exit(1)

print(f"✓ Verified required default ROMs ({', '.join(required_roms)}) present in pc-bios asset bundle!")
PY_SCRIPT

echo "=== Verified Output Directory (${OUTPUT_DIR}) ==="
ls -lh "${OUTPUT_DIR}"

echo "=== Verified Assets Directory (${ASSETS_DIR}) ==="
ls -lh "${ASSETS_DIR}" | head -n 30

