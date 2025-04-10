import os
from dataclasses import dataclass
from decimal import Decimal, ROUND_HALF_UP

from mt5linux import MetaTrader5


class MT5InvalidCreds(Exception):
    def __init__(self, message):
        super().__init__(message)

@dataclass
class AccountBalance:
    currency: str
    balance: Decimal
    credit: Decimal
    profit: Decimal
    equity: Decimal
    margin: Decimal
    margin_free: Decimal
    margin_level: Decimal
    margin_so_call: Decimal
    margin_so_so: Decimal
    margin_initial: Decimal
    margin_maintenance: Decimal
    assets: Decimal
    liabilities: Decimal
    commission_blocked: Decimal

# Note: Use initialize with login, password and server to connect to the terminal, login() have weird behavior
class MT5Service:
    def __init__(self, server: str, host: str, port: int):
        self.server = server
        self.host = host
        self.port = port

    def check_credentials(self, account_number: int, pwd: str) -> bool:
        try:
            with MT5Session(self.host, self.port, account_number, pwd, self.server) as session:
                return True
        except MT5InvalidCreds as e:
            print(f"Invalid credentials: {e}")
        except ValueError as e:
            print(f"Exception caught: {e}")

        return False

    def get_balance(self, account_number: int, pwd: str) -> AccountBalance:
        try:
            with MT5Session(self.host, self.port, account_number, pwd, self.server) as session:
                acc_info = session.account_info()
                print(acc_info)
                reference = Decimal("0.0001")
                balance = AccountBalance(acc_info.currency,
                                         Decimal(acc_info.balance).quantize(reference, rounding=ROUND_HALF_UP),
                                         Decimal(acc_info.credit).quantize(reference, rounding=ROUND_HALF_UP),
                                         Decimal(acc_info.profit).quantize(reference, rounding=ROUND_HALF_UP),
                                         Decimal(acc_info.equity).quantize(reference, rounding=ROUND_HALF_UP),
                                         Decimal(acc_info.margin).quantize(reference, rounding=ROUND_HALF_UP),
                                         Decimal(acc_info.margin_free).quantize(reference, rounding=ROUND_HALF_UP),
                                         Decimal(acc_info.margin_level).quantize(reference, rounding=ROUND_HALF_UP),
                                         Decimal(acc_info.margin_so_call).quantize(reference, rounding=ROUND_HALF_UP),
                                         Decimal(acc_info.margin_so_so).quantize(reference, rounding=ROUND_HALF_UP),
                                         Decimal(acc_info.margin_initial).quantize(reference, rounding=ROUND_HALF_UP),
                                         Decimal(acc_info.margin_maintenance).quantize(reference, rounding=ROUND_HALF_UP),
                                         Decimal(acc_info.assets).quantize(reference, rounding=ROUND_HALF_UP),
                                         Decimal(acc_info.liabilities).quantize(reference, rounding=ROUND_HALF_UP),
                                         Decimal(acc_info.commission_blocked).quantize(reference, rounding=ROUND_HALF_UP)
                                         )
                return balance
        except MT5InvalidCreds as e:
            print(f"Invalid credentials: {e}")
        except ValueError as e:
            print(f"Exception caught: {e}")

        return False




class MT5Session:
    def __init__(self, host: str, port: int, account_number: int, pwd: str, server: str):
        self.host = host
        self.port = port
        self.account_number = account_number
        self.pwd = pwd
        self.server = server

    def __enter__(self):
        self.mt5 = MetaTrader5(
            host=self.host,
            port=self.port
        )
        # LOGIN - number, pwd - string, server - string, otherwise will not work
        result_init = self.mt5.initialize(login=int(self.account_number), password=self.pwd, server=self.server)
        if not result_init:
            raise MT5InvalidCreds(f"Incorrect trade account credentials")

        return self.mt5

    def __exit__(self, exc_type, exc_value, exc_tb):
        self.mt5.shutdown()

server_env = os.getenv("MT5_SERVER")
host_env = os.getenv("MT5_HOST")
port_env = os.getenv("MT5_PORT")

def is_none_or_empty(string):
    return string is None or string.strip() == ""

if is_none_or_empty(server_env) or is_none_or_empty(host_env) or is_none_or_empty(port_env):
    raise ValueError("MT5_HOST, MT5_PORT, and MT5_SERVER environment variables must be set")

mt5service = MT5Service(server_env, host_env, int(port_env))

