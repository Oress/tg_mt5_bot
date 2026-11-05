#!/usr/bin/env bash
set -euo pipefail
mkdir -p /opt/terminal/MQL5/Experts
cp /opt/gateway/ea/GatewayFeed.mq5 /opt/terminal/MQL5/Experts/
rm -f /opt/terminal/MQL5/Experts/GatewayFeed.ex5
source_path=$(winepath -w /opt/terminal/MQL5/Experts/GatewayFeed.mq5)
set +e
timeout 120s xvfb-run -a wine /opt/terminal/metaeditor64.exe /portable "/compile:$source_path" /log
set -e
python3 - <<'PY'
from pathlib import Path
folder = Path('/opt/terminal/MQL5/Experts')
log = folder / 'GatewayFeed.log'
if log.exists():
    raw = log.read_bytes()
    print(raw.decode('utf-16' if raw.startswith(b'\xff\xfe') else 'utf-8', errors='replace'))
if not (folder / 'GatewayFeed.ex5').is_file():
    raise SystemExit('EA compilation failed: no EX5 produced')
PY
wineserver -k || true
