import asyncio
from collections import namedtuple
import importlib.util
import json
from pathlib import Path
import tempfile
import threading
import unittest

from websockets.asyncio.client import connect
from websockets.asyncio.server import serve
from websockets.exceptions import InvalidStatus

from gateway.server import Gateway
from gateway.terminal import GatewayError, Terminal


def record(**values):
    return namedtuple("Record", values)(**values)


class FakeMT5:
    ACCOUNT_MARGIN_MODE_RETAIL_NETTING = 0
    TRADE_ACTION_DEAL = 1
    ORDER_TYPE_BUY = 0
    ORDER_TYPE_SELL = 1
    ORDER_TIME_GTC = 0
    ORDER_FILLING_FOK = 0

    def __init__(self):
        self.account = None
        self.sent = []
        self.logins = []
        self.fail_order = False
        self.disabled = False
        self.live = True
        self.margin_mode = 0

    def initialize(self, path, **kwargs):
        return True

    def login(self, account, password, server, **kwargs):
        self.logins.append(account)
        if password != "demo-password":
            return False  # MT5 may retain the previous account after a failed login.
        self.account = (account, server)
        return True

    def terminal_info(self):
        return record(connected=self.live, trade_allowed=True, tradeapi_disabled=self.disabled, build=6000)

    def account_info(self):
        if self.account is None:
            return None
        return record(login=self.account[0], server=self.account[1], trade_allowed=True,
                      trade_expert=True, margin_mode=self.margin_mode, balance=100)

    def last_error(self):
        return (-6, "deliberately private diagnostic")

    def shutdown(self):
        pass

    def symbol_select(self, symbol, selected):
        return True

    def symbol_info_tick(self, symbol):
        return record(time=123, bid=1.1, ask=1.2)

    def positions_get(self, **kwargs):
        return []

    def order_check(self, order):
        return record(retcode=0)

    def order_send(self, order):
        self.sent.append((self.account, order))
        if self.fail_order:
            return None
        return record(retcode=10009, order=42, deal=43, request=record(**order))


def credentials(account=11):
    return {"account_number": account, "password": "demo-password", "server": "Demo"}


def order_params(account=11):
    return dict(credentials(account), symbol="EURUSD", volume=0.1, comment="trade-001")


class TerminalTests(unittest.TestCase):
    def setUp(self):
        self.folder = tempfile.TemporaryDirectory()
        self.addCleanup(self.folder.cleanup)
        self.fake = FakeMT5()
        self.terminal = Terminal(self.fake, "terminal.exe", "Demo", str(Path(self.folder.name) / "orders.db"), True)
        self.addCleanup(self.terminal.close)

    def test_switch_and_verify_actual_identity(self):
        self.terminal.execute("1", "login", credentials(11))
        self.terminal.execute("2", "account-info", credentials(22))
        self.assertEqual(self.fake.account, (22, "Demo"))
        self.assertEqual(self.terminal.identity, (22, "Demo"))
        self.terminal.execute("3", "account-info", credentials(22))
        self.assertEqual(self.fake.logins, [11, 22])

    def test_failed_switch_does_not_leave_old_account_ready(self):
        self.terminal.execute("1", "login", credentials())
        with self.assertRaises(GatewayError) as error:
            self.terminal.execute("2", "login", dict(credentials(22), password="wrong"))
        self.assertEqual(error.exception.code, "LOGIN_FAILED")
        self.assertNotIn("private diagnostic", str(error.exception))
        self.assertEqual(self.fake.account, (11, "Demo"))
        self.assertIsNone(self.terminal.identity)
        self.assertFalse(self.terminal.connected)

    def test_sdk_cannot_start_terminal_without_startup_configuration(self):
        self.terminal.is_running = lambda: False
        with self.assertRaises(GatewayError) as error:
            self.terminal.execute("1", "login", credentials())
        self.assertEqual(error.exception.code, "TERMINAL_STARTING")
        self.assertEqual(self.fake.logins, [])

    def test_transport_failure_is_not_reported_as_bad_credentials(self):
        self.fake.initialize = lambda *args, **kwargs: False
        self.fake.last_error = lambda: (-10005, "timeout")
        with self.assertRaises(GatewayError) as error:
            self.terminal.execute("1", "check-credentials", credentials())
        self.assertEqual(error.exception.code, "MT5_CONNECTION_FAILED")

    def test_external_account_change_requires_login_again(self):
        self.terminal.execute("1", "login", credentials())
        self.fake.account = (99, "Other")
        self.terminal.execute("2", "place-short-order", order_params())
        self.assertEqual(self.fake.sent[0][0], (11, "Demo"))

    def test_duplicate_order_survives_gateway_restart(self):
        result = self.terminal.execute("1", "place-short-order", order_params())
        self.terminal.close()
        restarted = Terminal(self.fake, "terminal.exe", "Demo", self.terminal.journal, True)
        self.addCleanup(restarted.close)
        self.assertEqual(result, restarted.execute("new-request-id", "place-short-order", order_params()))
        self.assertEqual(len(self.fake.sent), 1)
        self.assertNotIn(b"demo-password", Path(self.terminal.journal).read_bytes())

    def test_unknown_order_is_not_resent(self):
        self.fake.fail_order = True
        for _ in range(2):
            with self.assertRaises(GatewayError) as error:
                self.terminal.execute("1", "place-short-order", order_params())
            self.assertEqual(error.exception.code, "EXECUTION_UNKNOWN")
        self.assertEqual(len(self.fake.sent), 1)

    def test_duplicate_with_changed_volume_is_rejected(self):
        self.terminal.execute("1", "place-short-order", order_params())
        with self.assertRaises(GatewayError) as error:
            self.terminal.execute("2", "place-short-order", dict(order_params(), volume=0.2))
        self.assertEqual(error.exception.code, "DUPLICATE_CONFLICT")
        self.assertEqual(len(self.fake.sent), 1)

    def test_writes_require_explicit_enable_and_netting_account(self):
        self.terminal.allow_trading = False
        with self.assertRaises(GatewayError) as error:
            self.terminal.execute("1", "place-short-order", order_params())
        self.assertEqual(error.exception.code, "TRADING_DISABLED")
        self.terminal.allow_trading = True
        self.fake.margin_mode = 2
        with self.assertRaises(GatewayError) as error:
            self.terminal.execute("2", "place-short-order", order_params())
        self.assertEqual(error.exception.code, "UNSUPPORTED_ACCOUNT")
        self.assertEqual(self.fake.sent, [])

    def test_permission_setup_is_verified_through_sdk(self):
        self.fake.disabled = True
        self.terminal.configure_permissions = lambda: None
        self.terminal.execute("1", "login", credentials())
        self.assertFalse(self.terminal.trading_ready)
        self.terminal.configure_permissions = lambda: setattr(self.fake, "disabled", False)
        self.terminal.execute("2", "login", credentials())
        self.assertTrue(self.terminal.trading_ready)


class SocketTests(unittest.IsolatedAsyncioTestCase):
    async def asyncSetUp(self):
        self.folder = tempfile.TemporaryDirectory()
        self.fake = FakeMT5()
        self.terminal = Terminal(self.fake, "terminal.exe", "Demo", str(Path(self.folder.name) / "orders.db"))
        self.gateway = Gateway(self.terminal, "a" * 32)
        self.server = await serve(self.gateway.handle, "127.0.0.1", 0, process_request=self.gateway.handshake)
        self.url = f"ws://127.0.0.1:{self.server.sockets[0].getsockname()[1]}/ws"
        self.feed_server = await asyncio.start_server(self.gateway.receive_feed, "127.0.0.1", 0, limit=8192)
        self.feed_port = self.feed_server.sockets[0].getsockname()[1]

    async def asyncTearDown(self):
        self.server.close()
        await self.server.wait_closed()
        self.feed_server.close()
        await self.feed_server.wait_closed()
        await self.gateway.call(self.terminal.close)
        self.gateway.executor.shutdown()
        self.folder.cleanup()

    def client(self):
        return connect(self.url, additional_headers={"Authorization": "Bearer " + "a" * 32}, proxy=None)

    async def request(self, websocket, action, params=None, request_id="request-1"):
        await websocket.send(json.dumps({"id": request_id, "action": action, "params": params or {}}))
        return json.loads(await asyncio.wait_for(websocket.recv(), 2))

    async def test_authentication_and_malformed_message(self):
        with self.assertRaises(InvalidStatus) as error:
            async with connect(self.url, proxy=None):
                pass
        self.assertEqual(error.exception.response.status_code, 401)
        async with self.client() as websocket:
            await websocket.send("[]")
            self.assertEqual(json.loads(await websocket.recv())["error"]["code"], "INVALID_REQUEST")
            response = await self.request(websocket, "login", credentials())
            self.assertTrue(response["ok"])
            self.assertEqual(response["result"]["login"], 11)

    async def test_fragmented_request_and_response_correlation(self):
        async with self.client() as websocket:
            payload = json.dumps({"id": "correlation", "action": "login", "params": credentials()})
            await websocket.send([payload[:20], payload[20:]])
            response = json.loads(await websocket.recv())
            self.assertEqual(response["id"], "correlation")
            self.assertTrue(response["ok"])

    async def test_feed_only_reaches_matching_account_subscription(self):
        async with self.client() as websocket:
            await self.request(websocket, "subscribe", credentials())
            reader, writer = await asyncio.open_connection("127.0.0.1", self.feed_port)
            for account in (22, 11):
                writer.write(json.dumps({"type": "heartbeat", "account_number": account,
                                         "server": "Demo", "symbol": "EURUSD"}).encode() + b"\n")
            await writer.drain()
            frame = json.loads(await asyncio.wait_for(websocket.recv(), 2))
            self.assertEqual(frame["account_number"], 11)
            self.assertTrue(self.gateway.health()["feed_ready"])
            await self.request(websocket, "login", credentials(22))
            self.assertFalse(self.gateway.health()["feed_ready"])
            writer.close()
            await writer.wait_closed()

    async def test_cancelled_caller_cannot_release_inflight_sdk_operation(self):
        entered, release = threading.Event(), threading.Event()

        def blocking():
            entered.set()
            release.wait(3)

        task = asyncio.create_task(self.gateway.call(blocking))
        await asyncio.to_thread(entered.wait, 2)
        task.cancel()
        await asyncio.sleep(0.02)
        self.assertTrue(self.gateway.lock.locked())
        self.assertTrue(self.gateway.suspended)
        release.set()
        with self.assertRaises(asyncio.CancelledError):
            await task
        self.assertFalse(self.gateway.lock.locked())


class ConfigTests(unittest.TestCase):
    def test_startup_config_loads_ea_without_credentials(self):
        source = Path(__file__).parents[1] / "scripts" / "configure.py"
        spec = importlib.util.spec_from_file_location("configure", source)
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        with tempfile.TemporaryDirectory() as folder:
            config = module.configure(folder, "EURUSDz").read_text(encoding="utf-16")
            self.assertIn("Expert=GatewayFeed.ex5", config)
            self.assertIn("AllowDllImport=1", config)
            self.assertIn("Account=0", config)
            self.assertIn("Symbol=EURUSDz", config)
            self.assertNotIn("Password", config)
            with self.assertRaises(ValueError):
                module.configure(folder, "EURUSD\n[Common]\nPassword=bad")


if __name__ == "__main__":
    unittest.main()
