#!/usr/bin/env bash
# Verify (format + type-check + lint), then build the anki-viktor DEBUG apk.
# Installs on the phone as com.ichi2.anki.viktor.debug (red icon), alongside the stable app.
set -euo pipefail
cd /home/viktor/Anki-Android
./tools/verify.sh
./gradlew assembleFullDebug
# arm64-v8a = every real phone since ~2017. (Build emits per-ABI APKs, no universal.)
echo "APK: $(ls -t AnkiDroid/build/outputs/apk/full/debug/*arm64-v8a*.apk | head -1)"
