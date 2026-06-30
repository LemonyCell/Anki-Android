#!/usr/bin/env bash
# Verify the code: format + type-check + lint. Run before building or publishing.
#   format     -> ktlintFormat (auto-fixes Kotlin style)
#   type-check -> compiles the Kotlin (fails on type errors)
#   lint       -> release vital lint (the fatal lint that gates a release build)
set -euo pipefail
cd /home/viktor/Anki-Android
./gradlew ktlintFormat :AnkiDroid:compileFullDebugKotlin :AnkiDroid:lintVitalFullRelease
echo "verify OK"
