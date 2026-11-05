import asyncio
from concurrent.futures import ThreadPoolExecutor
from contextlib import suppress
from functools import partial
from http import HTTPStatus
import json
import logging
import secrets
import time

from websockets.asyncio.server import serve
from websockets.exceptions import ConnectionClosed

from .terminal import GatewayError

LOG = logging.getLogger(__name__)


class Gateway:
    def __init__(self, terminal, token, host="0.0.0.0", port=8000, feed_port=18823, feed_symbol="EURUSD"):
        if not token or len(token) < 32:
            raise ValueError("MT5_GATEWAY_TOKEN must contain at least 32 characters")
        self.terminal, self.token = terminal, token
        self.host, self.port, self.feed_port, self.feed_symbol = host, port, feed_port, feed_symbol
        self.executor = ThreadPoolExecutor(max_workers=1, thread_name_prefix="mt5")
        self.lock = asyncio.Lock()
        self.clients = {}
        self.feed = {}
        self.snapshot = {"connected": False, "trading_ready": False}
        self.snapshot_at = 0.0
        self.active_identity = None
        self.generation = 0
        self.suspended = True
        self.last_feed_frame = 0.0

    async def call(self, function, *args):
        # The lock covers the entire SDK operation, including account selection.
        async with self.lock:
            self.suspended = True
            try:
                operation = asyncio.get_running_loop().run_in_executor(self.executor, partial(function, *args))
                try:
                    result = await asyncio.shield(operation)
                except asyncio.CancelledError:
                    # A disconnected caller cannot cancel an order already inside
                    # the SDK. Keep ownership until it returns and is journalled.
                    await operation
                    raise
                return result
            finally:
                if self.generation != self.terminal.generation:
                    self.feed.clear()
                    self.generation = self.terminal.generation
                    for client in self.clients.values():
                        while not client[1].empty():
                            client[1].get_nowait()
                self.active_identity = self.terminal.identity
                self.suspended = False

    def health(self):
        fresh = time.monotonic() - self.snapshot_at < 10
        identity = self.active_identity
        feed_at = self.feed.get((*identity, self.feed_symbol), 0) if identity else 0
        feed_ready = time.monotonic() - feed_at < 5
        return {"status": "ok", "connected": fresh and self.snapshot.get("connected", False),
                "trading_ready": fresh and self.snapshot.get("trading_ready", False),
                "feed_ready": feed_ready, "busy": self.lock.locked(),
                "ea_running": time.monotonic() - self.last_feed_frame < 5,
                "orders_enabled": self.terminal.allow_trading}

    def handshake(self, connection, request):
        if request.path in {"/health", "/ready"}:
            status = self.health()
            ready = status["connected"] and status["trading_ready"] and status["feed_ready"]
            code = HTTPStatus.OK if request.path == "/health" or ready else HTTPStatus.SERVICE_UNAVAILABLE
            return connection.respond(code, json.dumps(status) + "\n")
        if request.path != "/ws":
            return connection.respond(HTTPStatus.NOT_FOUND, "Not found\n")
        values = request.headers.get_all("Authorization")
        if len(values) != 1 or not secrets.compare_digest(values[0], "Bearer " + self.token):
            return connection.respond(HTTPStatus.UNAUTHORIZED, "Unauthorized\n")
        return None

    async def handle(self, websocket):
        queue = asyncio.Queue(maxsize=128)
        self.clients[websocket] = [None, queue]
        sender = asyncio.create_task(self.send_events(websocket, queue))
        try:
            async for raw in websocket:
                request_id = None
                try:
                    request = json.loads(raw)
                    if not isinstance(request, dict):
                        raise ValueError()
                    request_id = request.get("id")
                    if not isinstance(request_id, str) or not 1 <= len(request_id) <= 128:
                        raise ValueError()
                    action, params = request.get("action"), request.get("params", {})
                    if not isinstance(action, str) or not isinstance(params, dict):
                        raise ValueError()
                    if action == "ping":
                        result = self.health()
                    elif action == "unsubscribe":
                        self.clients[websocket][0] = None
                        result = {"subscribed": False}
                    elif action == "subscribe":
                        account = await self.call(self.terminal.execute, request_id, "login", params)
                        self.clients[websocket][0] = (account["login"], account["server"])
                        result = {"subscribed": True, "account_number": account["login"], "server": account["server"]}
                    else:
                        result = await self.call(self.terminal.execute, request_id, action, params)
                    reply = {"id": request_id, "ok": True, "result": result}
                except (ValueError, TypeError, KeyError):
                    reply = self.failure(request_id, "INVALID_REQUEST", "Expected id, action and params")
                except GatewayError as error:
                    reply = self.failure(request_id, error.code, str(error))
                except Exception:
                    LOG.error("Command failed; request payload omitted")
                    reply = self.failure(request_id, "INTERNAL_ERROR", "Command failed; inspect gateway health")
                await asyncio.wait_for(websocket.send(json.dumps(reply, allow_nan=False)), 5)
        except (ConnectionClosed, TimeoutError):
            pass
        finally:
            self.clients.pop(websocket, None)
            sender.cancel()
            with suppress(asyncio.CancelledError, ConnectionClosed, TimeoutError):
                await sender

    @staticmethod
    def failure(request_id, code, message):
        return {"id": request_id, "ok": False, "error": {"code": code, "message": message}}

    async def send_events(self, websocket, queue):
        try:
            while True:
                event = await queue.get()
                if not self.suspended and (event["account_number"], event["server"]) == self.active_identity:
                    await asyncio.wait_for(websocket.send(json.dumps(event)), 5)
        except TimeoutError:
            await websocket.close(1013, "Consumer is too slow")

    async def receive_feed(self, reader, writer):
        connection_generation = None
        try:
            while True:
                line = await asyncio.wait_for(reader.readline(), 10)
                if not line:
                    return
                frame = json.loads(line)
                if not isinstance(frame, dict) or frame.get("type") not in {"tick", "heartbeat"}:
                    raise ValueError()
                identity = (int(frame["account_number"]), str(frame["server"]))
                symbol = frame["symbol"]
                if not isinstance(symbol, str) or len(symbol) > 128:
                    raise ValueError()
                self.last_feed_frame = time.monotonic()
                if self.suspended or identity != self.active_identity:
                    continue
                if connection_generation is not None and connection_generation != self.generation:
                    return
                connection_generation = self.generation
                self.feed[(*identity, symbol)] = time.monotonic()
                frame.update(source="ea", generation=self.generation)
                for websocket, (target, queue) in list(self.clients.items()):
                    if target == identity:
                        try:
                            queue.put_nowait(frame)
                        except asyncio.QueueFull:
                            await websocket.close(1013, "Feed queue full; reconnect and refresh state")
        except (ValueError, KeyError, TypeError, TimeoutError, ConnectionError):
            pass
        finally:
            writer.close()
            with suppress(ConnectionError):
                await writer.wait_closed()

    async def monitor(self):
        while True:
            try:
                self.snapshot = await self.call(self.terminal.status)
                self.snapshot_at = time.monotonic()
            except Exception:
                self.snapshot = {"connected": False, "trading_ready": False}
            await asyncio.sleep(2)

    async def run(self):
        monitor = asyncio.create_task(self.monitor())
        try:
            async with await asyncio.start_server(self.receive_feed, "127.0.0.1", self.feed_port, limit=8192):
                async with serve(self.handle, self.host, self.port, process_request=self.handshake,
                                 origins=[None], max_size=65536, max_queue=16, compression=None):
                    LOG.info("Gateway listening on port %s; local EA feed on %s", self.port, self.feed_port)
                    await asyncio.Future()
        finally:
            monitor.cancel()
            with suppress(asyncio.CancelledError):
                await monitor
            await asyncio.get_running_loop().run_in_executor(self.executor, self.terminal.close)
            self.executor.shutdown(wait=True)
