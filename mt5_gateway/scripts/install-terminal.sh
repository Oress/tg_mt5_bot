#!/usr/bin/env bash
set -euo pipefail
curl --fail --location --retry 3 "$MT5_INSTALLER_URL" -o /tmp/mt5setup.exe
set +e
timeout 300s xvfb-run -a wine /tmp/mt5setup.exe /auto
result=$?
set -e
wineserver -k || true
if [[ "$result" != 0 && "$result" != 124 ]]; then exit "$result"; fi
terminal=$(find "$WINEPREFIX/drive_c" -name terminal64.exe -print -quit)
test -n "$terminal"
test -f "$(dirname "$terminal")/metaeditor64.exe"
mkdir -p /opt/terminal
cp -a "$(dirname "$terminal")/." /opt/terminal/
rm -f /tmp/mt5setup.exe
