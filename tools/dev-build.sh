#!/usr/bin/env bash
# Format Kotlin, then build the anki-viktor DEBUG apk. That's it.
# Installs on the phone as com.ichi2.anki.viktor.debug (red icon), alongside the stable app.
set -euo pipefail
cd /home/viktor/Anki-Android
./gradlew ktlintFormat assembleFullDebug
# arm64-v8a = every real phone since ~2017. (Build emits per-ABI APKs, no universal.)
echo "APK: $(ls -t AnkiDroid/build/outputs/apk/full/debug/*arm64-v8a*.apk | head -1)"
