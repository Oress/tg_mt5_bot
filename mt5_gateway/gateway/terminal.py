"""Blocking MT5 operations. Call this object only from its single worker thread."""

import hashlib
import hmac
import json
import math
import secrets
import sqlite3
from pathlib import Path


class GatewayError(Exception):
    def __init__(self, code, message):
        super().__init__(message)
        self.code = code


def plain(value):
    if hasattr(value, "_asdict"):
        return {key: plain(item) for key, item in value._asdict().items()}
    if isinstance(value, dict):
        return {key: plain(item) for key, item in value.items()}
    if isinstance(value, (list, tuple)):
        return [plain(item) for item in value]
    return value


class Terminal:
    WRITES = {"place-short-order", "close-short-order"}
    ACTIONS = WRITES | {"login", "check-credentials", "account-info", "symbol-info",
                        "get-price-tick", "get-price-for-short", "positions-get",
                        "positions-get-all", "history-deals", "determine-account-type"}

    def __init__(self, mt5, path, server, journal, allow_trading=False, configure_permissions=None,
                 is_running=lambda: True):
        self.mt5, self.path, self.server = mt5, path, server
        self.journal = journal
        self.allow_trading = allow_trading
        self.configure_permissions = configure_permissions
        self.is_running = is_running
        self.identity = None
        self.credential_digest = None
        self.secret = secrets.token_bytes(32)
        self.connected = False
        self.trading_ready = False
        self.generation = 0
        self.db = None

    def invalidate(self):
        self.identity = None
        self.credential_digest = None
        self.connected = self.trading_ready = False
        self.generation += 1

    def error(self, code="MT5_ERROR"):
        # Never include SDK messages: some builds echo connection details.
        number = self.mt5.last_error()[0]
        if code == "LOGIN_FAILED" and number != -6:
            code = "MT5_CONNECTION_FAILED"
        return GatewayError(code, f"MT5 operation failed (code {number})")

    def require(self, result):
        if result is None:
            raise self.error()
        return result

    def login(self, params, force=False):
        account = params.get("account_number")
        password = params.get("password")
        server = params.get("server") or self.server
        if (isinstance(account, bool) or not isinstance(account, int) or account <= 0
                or not isinstance(password, str) or not password
                or not isinstance(server, str) or not server):
            raise GatewayError("INVALID_CREDENTIALS", "account_number, password and server are required")
        identity = (account, server)
        digest = hmac.digest(self.secret, json.dumps([account, server, password]).encode(), "sha256")
        info = self.mt5.account_info() if self.connected else None
        terminal = self.mt5.terminal_info() if self.connected else None
        same = (not force and self.identity == identity and self.credential_digest == digest
                and info is not None and (info.login, info.server) == identity
                and terminal is not None and terminal.connected)
        if not same:
            self.invalidate()
            if not self.is_running():
                raise GatewayError("TERMINAL_STARTING", "Waiting for the supervisor to start MT5")
            if not self.mt5.initialize(self.path, login=account, password=password,
                                       server=server, timeout=15000, portable=True):
                raise self.error("LOGIN_FAILED")
            if not self.mt5.login(account, password=password, server=server, timeout=15000):
                raise self.error("LOGIN_FAILED")
            info, terminal = self.mt5.account_info(), self.mt5.terminal_info()
            if (info is None or terminal is None or not terminal.connected
                    or (info.login, info.server) != identity):
                raise GatewayError("ACCOUNT_MISMATCH", "Terminal did not confirm the requested account")
            self.identity, self.credential_digest = identity, digest
            self.connected = True
        if terminal.tradeapi_disabled and self.configure_permissions is not None:
            try:
                self.configure_permissions()
                terminal = self.require(self.mt5.terminal_info())
            except Exception:
                raise GatewayError("PERMISSION_SETUP_FAILED", "Could not enable MT5 Python trading automatically") from None
        self.trading_ready = bool(terminal.trade_allowed and not terminal.tradeapi_disabled
                                  and info.trade_allowed and info.trade_expert)
        return info

    def execute(self, request_id, action, params):
        if action not in self.ACTIONS:
            raise GatewayError("UNKNOWN_ACTION", "Unsupported command")
        try:
            account = self.login(params, force=action in {"login", "check-credentials"})
        except GatewayError as error:
            if action == "check-credentials" and error.code == "LOGIN_FAILED":
                return {"valid": False}
            raise
        if action == "check-credentials":
            return {"valid": True}
        if action in {"login", "account-info"}:
            return plain(account)
        if action == "positions-get-all":
            return {"positions": plain(self.require(self.mt5.positions_get()))}
        if action == "history-deals":
            ticket, position = params.get("ticket"), params.get("position_id")
            if ticket is not None:
                values = self.mt5.history_deals_get(ticket=int(ticket))
            elif position is not None:
                values = self.mt5.history_deals_get(position=int(position))
            else:
                raise GatewayError("INVALID_REQUEST", "ticket or position_id is required")
            return {"history": plain(self.require(values))}
        if action == "determine-account-type":
            symbols = self.require(self.mt5.symbols_get())
            for symbol in symbols:
                if symbol.currency_base == "EUR" and symbol.currency_profit == "USD":
                    kind = {"EURUSD": "STANDARD", "EURUSDz": "ZERO", "EURUSDm": "MINI"}.get(symbol.name)
                    if kind:
                        return {"acc_type": kind}
            raise GatewayError("UNKNOWN_ACCOUNT_TYPE", "No supported EURUSD symbol was found")
        symbol = params.get("symbol")
        if not isinstance(symbol, str) or not symbol or len(symbol) > 128:
            raise GatewayError("INVALID_REQUEST", "A symbol is required")
        if not self.mt5.symbol_select(symbol, True):
            raise self.error("UNKNOWN_SYMBOL")
        if action == "symbol-info":
            return {"info": plain(self.require(self.mt5.symbol_info(symbol)))}
        if action == "positions-get":
            return {"positions": plain(self.require(self.mt5.positions_get(symbol=symbol)))}
        if action in self.WRITES:
            return self.order(request_id, action, params)
        tick = self.require(self.mt5.symbol_info_tick(symbol))
        return {"price": tick.bid} if action == "get-price-for-short" else {"tick": plain(tick)}

    def journal_connection(self):
        if self.db is None:
            Path(self.journal).parent.mkdir(parents=True, exist_ok=True)
            self.db = sqlite3.connect(self.journal)
            self.db.execute("PRAGMA journal_mode=WAL")
            self.db.execute("PRAGMA synchronous=FULL")
            self.db.execute("CREATE TABLE IF NOT EXISTS orders "
                            "(id TEXT PRIMARY KEY, digest TEXT NOT NULL, result TEXT)")
            self.db.commit()
        return self.db

    def order(self, request_id, action, params):
        if not self.allow_trading:
            raise GatewayError("TRADING_DISABLED", "Enable MT5_ALLOW_TRADING to execute orders")
        if not self.trading_ready:
            raise GatewayError("TRADING_NOT_READY", "Terminal or account trading permission is disabled")
        # The application supports netting accounts; reject hedging before sending.
        account = self.require(self.mt5.account_info())
        if (account.login, account.server) != self.identity:
            self.invalidate()
            raise GatewayError("ACCOUNT_MISMATCH", "Account changed before order execution")
        if account.margin_mode != self.mt5.ACCOUNT_MARGIN_MODE_RETAIL_NETTING:
            raise GatewayError("UNSUPPORTED_ACCOUNT", "Only retail netting accounts are supported")
        volume = params.get("volume")
        if isinstance(volume, bool) or not isinstance(volume, (float, int)) or not math.isfinite(volume) or volume <= 0:
            raise GatewayError("INVALID_REQUEST", "volume must be a positive finite number")
        comment = params.get("comment")
        if not isinstance(comment, str) or not comment or len(comment) > 31:
            raise GatewayError("INVALID_REQUEST", "A correlation comment of 1–31 characters is required")
        position = params.get("position_id")
        if position is not None and (isinstance(position, bool) or not isinstance(position, int) or position <= 0):
            raise GatewayError("INVALID_REQUEST", "position_id must be a positive integer")
        body = {key: value for key, value in params.items() if key != "password"}
        body.update(server=self.identity[1], action=action)
        digest = hashlib.sha256(json.dumps(body, sort_keys=True, allow_nan=False).encode()).hexdigest()
        # Stable across reconnects and Java restarts. Passwords are never persisted.
        key = json.dumps([*self.identity, action, comment])
        db = self.journal_connection()
        previous = db.execute("SELECT digest, result FROM orders WHERE id=?", (key,)).fetchone()
        if previous:
            if previous[0] != digest:
                raise GatewayError("DUPLICATE_CONFLICT", "Correlation ID was used with different order parameters")
            if previous[1] is None:
                raise GatewayError("EXECUTION_UNKNOWN", "Reconcile this order with broker history before retrying")
            return json.loads(previous[1])
        tick = self.require(self.mt5.symbol_info_tick(params["symbol"]))
        buy = action == "close-short-order"
        order = dict(action=self.mt5.TRADE_ACTION_DEAL, symbol=params["symbol"], volume=float(volume),
                     type=self.mt5.ORDER_TYPE_BUY if buy else self.mt5.ORDER_TYPE_SELL,
                     price=tick.ask if buy else tick.bid, comment=comment,
                     type_time=self.mt5.ORDER_TIME_GTC, type_filling=self.mt5.ORDER_FILLING_FOK)
        if position is not None:
            positions = self.require(self.mt5.positions_get(ticket=position))
            if not positions or positions[0].symbol != params["symbol"]:
                raise GatewayError("INVALID_POSITION", "Position does not belong to this account and symbol")
            order["position"] = position
        check = self.require(self.mt5.order_check(order))
        if check.retcode != 0:
            raise GatewayError("ORDER_REJECTED", f"MT5 order check rejected the order ({check.retcode})")
        db.execute("INSERT INTO orders(id,digest) VALUES (?,?)", (key, digest))
        db.commit()
        try:
            result = self.mt5.order_send(order)
            if result is None:
                raise GatewayError("EXECUTION_UNKNOWN", "MT5 returned no result; reconcile broker history")
            payload = {"order": plain(result)}
            db.execute("UPDATE orders SET result=? WHERE id=?", (json.dumps(payload), key))
            db.commit()
            return payload
        except GatewayError:
            raise
        except Exception:
            raise GatewayError("EXECUTION_UNKNOWN", "Order outcome is unknown; reconcile broker history") from None

    def status(self):
        info = self.mt5.terminal_info() if self.connected else None
        account = self.mt5.account_info() if self.connected else None
        if (info is None or not info.connected or account is None
                or (account.login, account.server) != self.identity):
            if self.connected:
                self.invalidate()
            return {"connected": False, "trading_ready": False}
        self.trading_ready = bool(info.trade_allowed and not info.tradeapi_disabled
                                  and account.trade_allowed and account.trade_expert)
        return {"connected": True, "trading_ready": self.trading_ready,
                "build": info.build, "account_number": account.login, "server": account.server}

    def close(self):
        self.mt5.shutdown()
        if self.db is not None:
            self.db.close()
