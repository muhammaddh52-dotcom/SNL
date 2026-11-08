#!/usr/bin/env bash
set -euo pipefail

echo "=================================================================="
echo " SAIFI NATIONAL LUBRICANTS (SNL) — Signed APK Build & Verification"
echo " Package ID: com.aistudio.snllubricants.pkb2b"
echo "=================================================================="

# 1. Build the signed APK using the registered keystore in debugConfig
gradle :app:assembleDebug

APK_PATH="app/build/outputs/apk/debug/app-debug.apk"
if [[ -f "$APK_PATH" ]]; then
  echo ""
  echo "[SUCCESS] Signed Side-Loadable APK generated:"
  ls -lh "$APK_PATH"
  echo ""
  echo "Signing Certificate Fingerprints (registered in google-services.json):"
  keytool -list -v -keystore debug.keystore -alias androiddebugkey -storepass android -keypass android | grep -E "SHA1:|SHA256:" || true
fi

# 2. Optional: If KEYSTORE_PATH, STORE_PASSWORD, and KEY_PASSWORD are set, also build assembleRelease
if [[ -n "${KEYSTORE_PATH:-}" && -f "${KEYSTORE_PATH:-}" ]]; then
  echo ""
  echo "Building release-signed APK using KEYSTORE_PATH=$KEYSTORE_PATH..."
  gradle :app:assembleRelease
  ls -lh app/build/outputs/apk/release/app-release.apk
fi
