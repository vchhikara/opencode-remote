#!/usr/bin/env bash
set -euo pipefail

# Verifies that native .so binaries packaged inside an APK/AAB are stripped.
# A properly stripped ELF binary contains only dynamic symbols (.dynsym),
# discarding static symbol tables (.symtab) and DWARF debug sections (.debug_*).
#
# This app bundles native code transitively via MLKit barcode scanning
# (libbarhopper_v3.so) and CameraX vendor extensions (libimage_processing_util_jni.so,
# libsurface_util_jni.so) — confirmed present in a real assembleDebug run, so this
# check has real libraries to verify, not a hypothetical.

if [[ $# -lt 1 ]]; then
    echo "Usage: $0 <path-to-apk-or-aab>"
    exit 1
fi

TARGET_ARCHIVE="$1"

if [[ ! -f "$TARGET_ARCHIVE" ]]; then
    echo "[-] Error: File not found: $TARGET_ARCHIVE"
    exit 1
fi

READELF_BIN=""
if command -v llvm-readelf &>/dev/null; then
    READELF_BIN="llvm-readelf"
elif command -v readelf &>/dev/null; then
    READELF_BIN="readelf"
elif [[ -n "${ANDROID_NDK_HOME:-}" ]]; then
    READELF_BIN=$(find "$ANDROID_NDK_HOME/toolchains/llvm/prebuilt" -name "llvm-readelf" 2>/dev/null | head -n 1)
fi

if [[ -z "$READELF_BIN" || ! -x "$(command -v "$READELF_BIN" 2>/dev/null || echo "$READELF_BIN")" ]]; then
    echo "[-] Error: Neither 'llvm-readelf' nor 'readelf' found in PATH or ANDROID_NDK_HOME."
    exit 1
fi

echo "[*] Using ELF analyzer: $READELF_BIN"

TMP_DIR=$(mktemp -d)
trap 'rm -rf "$TMP_DIR"' EXIT

echo "[*] Extracting native libraries from: $(basename "$TARGET_ARCHIVE")"
unzip -q "$TARGET_ARCHIVE" "*lib*/*.so" -d "$TMP_DIR" 2>/dev/null || {
    echo "[!] No native libraries (.so) detected in archive."
    exit 0
}

FAILED=0
TOTAL=0

while IFS= read -r -d '' so_file; do
    TOTAL=$((TOTAL + 1))
    rel_path="${so_file#"$TMP_DIR/"}"

    SECTION_OUTPUT=$("$READELF_BIN" -S "$so_file" 2>&1)

    HAS_SYMTAB=$(echo "$SECTION_OUTPUT" | grep -E '\.symtab' || true)
    HAS_DEBUG=$(echo "$SECTION_OUTPUT" | grep -E '\.debug_' || true)

    if [[ -n "$HAS_SYMTAB" || -n "$HAS_DEBUG" ]]; then
        echo "[FAIL] $rel_path"
        [[ -n "$HAS_SYMTAB" ]] && echo "       -> Contains unstripped symbol table (.symtab)"
        [[ -n "$HAS_DEBUG" ]]  && echo "       -> Contains debug sections (.debug_*)"
        FAILED=$((FAILED + 1))
    else
        echo "[PASS] $rel_path (Clean: only .dynsym present)"
    fi
done < <(find "$TMP_DIR" -type f -name "*.so" -print0)

echo "--------------------------------------------------------"
echo "Results: Total: $TOTAL | Passed: $((TOTAL - FAILED)) | Failed: $FAILED"

if [[ $FAILED -gt 0 ]]; then
    echo "[-] Audit failed: $FAILED unstripped native librar(ies) detected."
    exit 1
fi

echo "[+] Audit passed: All native libraries are fully stripped."
exit 0
