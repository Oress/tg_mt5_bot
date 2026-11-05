#!/usr/bin/env bash
set -euo pipefail
gateway_token=${MT5_GATEWAY_TOKEN:-}
if [[ ${#gateway_token} -lt 32 ]]; then
    echo 'Set MT5_GATEWAY_TOKEN to at least 32 random characters.' >&2
    exit 1
fi
mkdir -p /state/terminal
if [[ ! -f /state/terminal/terminal64.exe ]]; then
    cp -a /opt/terminal/. /state/terminal/
fi
# Refresh our EA even when a persistent terminal directory already exists.
mkdir -p /state/terminal/MQL5/Experts
cp /opt/terminal/MQL5/Experts/GatewayFeed.{mq5,ex5} /state/terminal/MQL5/Experts/
python3 /opt/gateway/scripts/configure.py
export MT5_TERMINAL_PATH='Z:\state\terminal\terminal64.exe'
export MT5_ORDER_JOURNAL='Z:\state\orders.sqlite3'
exec /usr/bin/supervisord -n -c /opt/gateway/supervisord.conf
