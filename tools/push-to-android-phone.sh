#!/usr/bin/env bash
# Build + install the debug apk onto your phone. Gradle picks the right ABI automatically.
# Wireless debugging: VPN must be OFF so the phone shows its LAN 192.168.x.x (not a 10.x VPN ip).
# Usage:
#   ./tools/dev-push.sh 192.168.1.50:39745   # first time this session: ip:port from
#                                            # Settings > Developer options > Wireless debugging
#   ./tools/dev-push.sh                      # later: phone already connected
set -euo pipefail
ADB=/home/viktor/Android/Sdk/platform-tools/adb
cd /home/viktor/Anki-Android
if [ $# -ge 1 ]; then
  "$ADB" connect "$1"
  export ANDROID_SERIAL="$1"
fi
./gradlew installFullDebug
echo "Installed full debug onto ${ANDROID_SERIAL:-the connected device}"
