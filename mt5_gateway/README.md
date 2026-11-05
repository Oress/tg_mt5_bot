# MT5 WebSocket worker

One Python gateway, one supervised MT5 terminal, and one automatically loaded quote EA per container. Java connects to `/ws`; the gateway imports MetaQuotes' Windows Python package directly. The EA sends local TCP notifications to `127.0.0.1:18823`. That port is not published.

## Validation status

The Python tests use a fake MT5 implementation and real loopback WebSocket connections. Java transport tests and the Java build pass. Docker Compose and shell/Python syntax have been checked.

**The image has not yet passed a native MT5 build or broker smoke test.** On the development ARM Mac, the x86 Wine process aborts in `ntdll/unix/virtual.c` under QEMU before the installer starts. EA compilation, the startup chart, DLL permissions, Python-trading permission automation, and account-switch/restart behavior still need the x86 validation below. This is an opt-in worker; existing HTTP URLs continue using the existing proxy.

## Build on an x86-64 Linux Docker host

Run from the repository root. The image installs Gerchik MT5, Wine 11, Windows Python 3.11.9, and pinned Python packages. MetaEditor compiles `GatewayFeed.mq5` during the build; a missing `.ex5` fails the build. The broker installer is fetched from Gerchik's MetaQuotes distribution URL. The terminal can self-update, so record its build when validating and recheck readiness after updates.

```bash
export MT5_GATEWAY_TOKEN="$(python3 -c 'import secrets; print(secrets.token_urlsafe(32))')"
export MT5_SERVER='YOUR_EXACT_GERCHIK_DEMO_SERVER'
export MT5_FEED_SYMBOL=EURUSD   # Use the broker's exact symbol, e.g. EURUSDz or EURUSDm.
export MT5_ALLOW_TRADING=0
docker compose -f docker-compose-gateway.yaml up --build -d
docker compose -f docker-compose-gateway.yaml logs -f mt5gateway
```

Retain the token securely for subsequent Compose commands and the Java client. Account credentials are supplied at runtime, not during the build and not through container environment variables. `KeepPrivate=0` is set in MT5's startup configuration; the gateway does not store passwords. The persistent volume holds terminal state and an order journal and must be retained across recreations.

At startup the worker:

1. Seeds a new terminal volume from the image and refreshes our compiled EA on an existing volume.
2. Writes a UTF-16 startup configuration with AlgoTrading/DLL imports enabled, account-change disabling turned off, and `[StartUp] Expert=GatewayFeed.ex5` on the configured symbol's M1 chart.
3. Starts MT5 with `/portable /config:...`. A supervisor outside Wine restarts exited terminal and gateway processes.
4. Starts the gateway independently of a broker login. `/health` is a liveness probe; `/ready` stays HTTP 503 until a requested account is connected, trading permissions are confirmed, and a fresh EA heartbeat is received.
5. On login, if MT5 reports its external Python trading API disabled, opens Options and clears the Python checkbox using Windows control handles. MT5 has no documented INI setting for that flag. Failure is reported as `PERMISSION_SETUP_FAILED`; a successful UI action is checked again through the SDK.

The EA uses the Windows/Wine-provided `ws2_32.dll` for its loopback TCP connection. This avoids depending on manual edits to MT5's SocketConnect/WebRequest URL allow-list. No custom DLL binary is required. DLL import permission is explicitly required for this EA.

## Read-only broker validation

On the test machine, install the client dependency in a local virtual environment:

```bash
python3 -m venv /tmp/mt5-gateway-test
/tmp/mt5-gateway-test/bin/pip install websockets==15.0.1
/tmp/mt5-gateway-test/bin/python mt5_gateway/scripts/smoke.py \
  --account DEMO_ACCOUNT_NUMBER --server "$MT5_SERVER" \
  --second-account SECOND_DEMO_ACCOUNT_NUMBER
```

Replace the account placeholders with numbers. The optional second account checks switching A → B → A. Passwords are prompted without echo. This script performs login, position reads and feed subscription only; it never submits orders. A heartbeat is sent even when the market is closed. Use demo accounts whose symbol matches `MT5_FEED_SYMBOL` for this first validation.

```bash
curl --fail http://127.0.0.1:8010/health
curl --fail http://127.0.0.1:8010/ready
docker compose -f docker-compose-gateway.yaml restart mt5gateway
# Rerun smoke.py to verify recovery and EA loading from the existing volume.
```

Also validate a fresh volume, a wrong password, a terminal process exit, and a disconnected WebSocket. A failed login must not leave the previous account ready. Do not delete the order journal to resolve an unknown execution; reconcile broker history first.

## Select the transport in Java

The existing `Mt5Service` contract is unchanged. HTTP(S) URLs keep the current implementation; WS(S) URLs select the new persistent client:

```bash
export APP_MT5_GATEWAY_TOKEN="$MT5_GATEWAY_TOKEN"
export APP_PYTHON_PROXY_URL=ws://127.0.0.1:8010/ws
# Set the corresponding APP_PYTHON_PROXY_URL_1 ... _10 for each auxiliary
# terminal being migrated. Each worker must have its own state volume.
```

The app assigns users across ten auxiliary terminals. Changing only the main URL changes credential validation/main-terminal calls, not every user's terminal. Migrate the assigned auxiliary URL too. When Java runs in the same Docker network, use `ws://mt5gateway:8000/ws`; localhost refers to Java's own container. For traffic between hosts, use an SSH tunnel or terminate TLS and use `wss://`. The supplied Compose file binds only to localhost.

The Java request/reply adapter doesn't subscribe to the quote stream. Subscription is available over the gateway protocol for future streaming consumers; existing Java tick reads still work over WebSocket commands.

## Protocol

Authenticate the WebSocket handshake with `Authorization: Bearer <token>`. Browser Origins are rejected; this endpoint is for the backend. Credentials and request bodies are omitted from gateway logs.

```json
{"id":"request-1","action":"login","params":{"account_number":123456,"password":"...","server":"Gerchik demo server"}}
```

Success: `{"id":"request-1","ok":true,"result":{...}}`.
Failure: `{"id":"request-1","ok":false,"error":{"code":"LOGIN_FAILED","message":"..."}}`.

Commands: `login`, `check-credentials`, `account-info`, `symbol-info`, `determine-account-type`, `positions-get`, `positions-get-all`, `get-price-tick`, `get-price-for-short`, `history-deals`, `place-short-order`, `close-short-order`, `subscribe`, `unsubscribe`, `ping`.

Trading/read commands use the current Java payload fields, including `account_number`, `password`, optional `server`, `symbol`, `position_id`, `volume`, and `comment`. Account selection and the operation run in one serialized worker. A cached login is reused only while credentials, actual account/server, and connection state match. A failed switch invalidates readiness even if MT5 retains its previous login. Reconnect the Java client on its next call; requests are never automatically resent.

`subscribe` takes account credentials and binds that socket's events to the confirmed account. Events contain `type`, `account_number`, `server`, `symbol`, `session`, `seq`, `source` and gateway `generation`. Switching accounts clears buffered events; old feed connections are rejected after a generation change. Heartbeats are for readiness. Tick notifications are not a complete historical tick archive: MT5 can coalesce `OnTick` callbacks and a disconnected or slow consumer can miss updates. Refresh state/history after reconnects.

Order execution is disabled by default (`MT5_ALLOW_TRADING=0`). When enabled, retail netting accounts and FOK market orders are supported. MT5 `order_check` must accept the request before submission; broker filling-mode incompatibility is returned as an error. Account/server, side and correlation comment identify an order in the persistent journal. A repeated identical order returns the saved result; conflicting parameters are rejected. The journal is committed before `order_send`. An interrupted/unknown submission stays blocked for reconciliation, including after a worker restart. The gateway does not promise exactly-once broker execution or implement automatic reconciliation.

One terminal has one active account. Switching away stops continuous monitoring of the old account. The initial image auto-loads one feed chart; additional symbols require additional EA charts or a later chart-management feature. Readiness checks use the configured feed symbol. Process supervision handles process exits; a hung SDK call yields stale readiness and currently requires an operator restart.

## Local checks without MT5

```bash
python3 -m venv /tmp/mt5-gateway-unit
/tmp/mt5-gateway-unit/bin/pip install -r mt5_gateway/requirements.txt
PYTHONPATH=mt5_gateway /tmp/mt5-gateway-unit/bin/python -m unittest discover -s mt5_gateway/tests -v
mvn -f hedging/pom.xml -Dskip.installnodenpm -Dskip.npm -Dtest=Mt5WebSocketTransportTest test
```

References: [MT5 startup configuration](https://www.metatrader5.com/en/terminal/help/start_advanced/start), [MetaEditor command-line compilation](https://www.metatrader5.com/en/metaeditor/help/beginning/integration_ide), [MetaQuotes Python API](https://www.mql5.com/en/docs/python_metatrader5). The nodalytics project was an architecture reference; this worker and EA are implemented in this repository.
