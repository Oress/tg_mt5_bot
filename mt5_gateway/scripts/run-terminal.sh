#!/usr/bin/env bash
set -euo pipefail
until xdpyinfo -display "$DISPLAY" >/dev/null 2>&1; do sleep 1; done
# A separate supervisor process owns this lifetime; an EA cannot restart its host.
exec wine /state/terminal/terminal64.exe /portable '/config:Z:\state\terminal\gateway.ini'
