#!/usr/bin/env bash
set -euo pipefail

OUTPUT_DIR="${1:-jniLibs-bundle/arm64-v8a}"
mkdir -p "${OUTPUT_DIR}"

SRC_FILE="app/src/main/cpp/native.cpp"
TARGET_SO="${OUTPUT_DIR}/libaxilbox-native.so"

echo "=== Compiling Native Engine Bridge (${TARGET_SO}) ==="

CXX_BIN=""

# 1. Look for Android NDK clang++
NDK_SEARCH_DIRS=(
    "${ANDROID_NDK_HOME:-}"
    "${ANDROID_NDK_ROOT:-}"
    /usr/local/lib/android/sdk/ndk/*
    /opt/android-sdk/ndk/*
    "$HOME/Android/Sdk/ndk"/*
)

for ndk in "${NDK_SEARCH_DIRS[@]}"; do
    if [[ -d "$ndk" ]]; then
        for candidate in "$ndk"/toolchains/llvm/prebuilt/*/bin/aarch64-linux-android*clang++; do
            if [[ -x "$candidate" ]]; then
                CXX_BIN="$candidate"
                break 2
            fi
        done
    fi
done

# 2. Check local Termux / Android / Cross environment
if [[ -z "$CXX_BIN" ]]; then
    if command -v aarch64-linux-android-clang++ >/dev/null 2>&1; then
        CXX_BIN="$(command -v aarch64-linux-android-clang++)"
    elif command -v clang++ >/dev/null 2>&1 && [[ "$(uname -m)" == "aarch64" ]]; then
        CXX_BIN="$(command -v clang++)"
    elif command -v aarch64-linux-gnu-g++ >/dev/null 2>&1; then
        CXX_BIN="$(command -v aarch64-linux-gnu-g++)"
    fi
fi

if [[ -z "$CXX_BIN" ]]; then
    echo "FATAL: Could not find suitable aarch64 C++ compiler (NDK clang++, Termux clang++, or aarch64-linux-gnu-g++)" >&2
    exit 1
fi

echo " -> Using compiler: ${CXX_BIN}"
"${CXX_BIN}" -shared -fPIC -O2 -Wall -std=c++17 \
    "${SRC_FILE}" \
    -o "${TARGET_SO}" \
    -llog

if command -v patchelf >/dev/null 2>&1; then
    patchelf --remove-rpath "${TARGET_SO}" 2>/dev/null || true
fi

echo "✓ Compiled ${TARGET_SO} successfully."

