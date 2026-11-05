#!/usr/bin/env bash
set -euo pipefail
until xdpyinfo -display "$DISPLAY" >/dev/null 2>&1; do sleep 1; done
cd /opt/gateway
exec wine 'C:\Python311\python.exe' -m gateway
