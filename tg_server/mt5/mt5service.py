import logging
import os
from dataclasses import dataclass
from decimal import Decimal

from mt5linux import MetaTrader5

from tg_server.constants.AccountType import AccountType
from tg_server.constants.Instruments import EURUSD
from tg_server.utils.decimals import round_flt_to_4_decimal

logger = logging.getLogger(__name__)

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

@dataclass
class SymbolInfo:
    description: str
    volume_min: Decimal
    volume_step: Decimal
    volume_max: Decimal
    volume_limit: Decimal
    trade_contract_size: Decimal

    def to_dict(self):
        return {
            "description": self.description,
            "volume_min": self.volume_min,
            "volume_step": self.volume_step,
            "volume_max": self.volume_max,
            "volume_limit": self.volume_limit,
            "trade_contract_size": self.trade_contract_size
        }

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
            logger.error(f"[MT5|Invalid Creds], account_number: {account_number}, server: {self.server}, host: {self.host}, port: {self.port}")
            print(f"Invalid credentials: {e}")
        except Exception as e:
            logger.error(f"[MT5|Unknown Exception]")
            print(f"Exception caught: {e}")

        return False

    def get_price_for_short(self, account_number: int, pwd: str, symbol: str) -> Decimal:
        logger.info(f"[MT5|get_price_for_short] account_number: {account_number}, symbol: {symbol}")
        try:
            with MT5Session(self.host, self.port, account_number, pwd, self.server) as session:
                selected=session.symbol_select(symbol, True)
                if not selected:
                    logger.error(f"[MT5|Symbol Select Failed], account_number: {account_number}, ")
                    print("Failed to select symbol, error code =",session.last_error())
                else:
                    info_tick = session.symbol_info_tick(symbol)
                    print('info_tick', info_tick)

                    price = info_tick.bid
                    return round_flt_to_4_decimal(price)
        except MT5InvalidCreds as e:
            logger.error(f"[MT5|Invalid Creds], account_number: {account_number}, server: {self.server}, host: {self.host}, port: {self.port}")
            print(f"Invalid credentials: {e}")
        except Exception as e:
            logger.error(f"[MT5|Unknown Exception]")
            print(f"Exception caught: {e}")

        return Decimal(0)

    def positions_get(self, account_number: int, pwd: str, symbol: str) -> bool:
        logger.info(f"[MT5|orders_get] account_number: {account_number}, symbol: {symbol}")
        selected = []
        try:
            with MT5Session(self.host, self.port, account_number, pwd, self.server) as session:
                selected = session.positions_get(symbol=symbol)
                if selected is None:
                    logger.warning(f"[MT5|orders_get], No orders for the account_number: {account_number}, symbol: {symbol}")
                    selected = []

        except MT5InvalidCreds as e:
            logger.error(f"[MT5|Invalid Creds], account_number: {account_number}, server: {self.server}, host: {self.host}, port: {self.port}")
            print(f"Invalid credentials: {e}")
        except Exception as e:
            logger.error(f"[MT5|Unknown Exception]")
            print(f"Exception caught: {e}")

        return selected

    def place_short_order(self, account_number: int, pwd: str, symbol: str, volume: Decimal):
        logger.info(f"[MT5|place_short_order] account_number: {account_number}, symbol: {symbol}, volume: {volume}")
        try:
            with MT5Session(self.host, self.port, account_number, pwd, self.server) as session:
                info_tick = session.symbol_info_tick(symbol)
                logger.info(f"[MT5|place_short_order] info_tick: {info_tick}")

                request = {
                    "action": MetaTrader5.TRADE_ACTION_DEAL,
                    "symbol": symbol,
                    "volume": float(volume),
                    "type": MetaTrader5.ORDER_TYPE_SELL,
                    "comment": f"TG Bot order open",
                    "price": info_tick.bid,
                    # "deviation": deviation,
                    # "type_time": MetaTrader5.ORDER_TIME_GTC,
                    # "type_filling": MetaTrader5.ORDER_FILLING_RETURN,
                }

                order_result = session.order_send(request)
                logger.info(f"[MT5|place_short_order] order_result: {order_result}")
                logger.info(f"[MT5|place_short_order] error: {session.last_error()}")
                return order_result
        except MT5InvalidCreds as e:
            logger.error(f"[MT5|Invalid Creds], account_number: {account_number}, server: {self.server}, host: {self.host}, port: {self.port}")
            print(f"Invalid credentials: {e}")
        except Exception as e:
            logger.error(f"[MT5|Unknown Exception]")
            print(f"Exception caught: {e}")

        return False

    def close_short_order(self, account_number: int, pwd: str, position_id: int, symbol: str, volume: Decimal):
        logger.info(f"[MT5|close_short_order] account_number: {account_number}, symbol: {symbol}, volume: {volume}")
        try:
            with MT5Session(self.host, self.port, account_number, pwd, self.server) as session:
                info_tick = session.symbol_info_tick(symbol)
                logger.info(f"[MT5|close_short_order] info_tick: {info_tick}")

                close_request={
                    "action": MetaTrader5.TRADE_ACTION_DEAL,
                    "symbol": symbol,
                    "volume": float(volume),
                    "type": MetaTrader5.ORDER_TYPE_BUY,
                    "position": position_id,
                    "price": info_tick.ask,
                    # "deviation": deviation,
                    # "magic": ea_magic_number,
                    "comment": "TG Bot order close",
                    "type_time": MetaTrader5.ORDER_TIME_GTC,
                    "type_filling": MetaTrader5.ORDER_FILLING_FOK,
                }

                order_result = session.order_send(close_request)
                logger.info(f"[MT5|close_short_order] order_result: {order_result}")
                logger.info(f"[MT5|close_short_order] error: {session.last_error()}")
                return order_result
        except MT5InvalidCreds as e:
            logger.error(f"[MT5|Invalid Creds], account_number: {account_number}, server: {self.server}, host: {self.host}, port: {self.port}")
            print(f"Invalid credentials: {e}")
        except Exception as e:
            logger.error(f"[MT5|Unknown Exception]")
            print(f"Exception caught: {e}")

        return False

    def symbol_info(self, account_number: int, pwd: str, symbol: str):
        logger.info(f"[MT5|symbol_info] account_number: {account_number}, symbol: {symbol}")
        try:
            with MT5Session(self.host, self.port, account_number, pwd, self.server) as session:
                selected=session.symbol_select(symbol, True)

                if not selected:
                    logger.error(f"[MT5|Symbol Info Failed], account_number: {account_number}, ")
                    print("[MT5|Symbol Info Failed] Failed to select symbol, error code =",session.last_error())
                else:
                    sym_info = session.symbol_info(symbol)
                    print('sym_info', sym_info)
                    logger.info(f"[MT5|Symbol Info Success]: {session.last_error()}")

                sym_info_model = SymbolInfo(
                    description=sym_info.description,
                    volume_min=round_flt_to_4_decimal(sym_info.volume_min),
                    volume_step=round_flt_to_4_decimal(sym_info.volume_step),
                    volume_max=round_flt_to_4_decimal(sym_info.volume_max),
                    volume_limit=round_flt_to_4_decimal(sym_info.volume_limit),
                    trade_contract_size=round_flt_to_4_decimal(sym_info.trade_contract_size)
                )
                return sym_info_model
        except MT5InvalidCreds as e:
            logger.error(f"[MT5|Invalid Creds], account_number: {account_number}, server: {self.server}, host: {self.host}, port: {self.port}")
            print(f"Invalid credentials: {e}")
        except Exception as e:
            logger.error(f"[MT5|Unknown Exception], account_number: {account_number}, server: {self.server}, host: {self.host}, port: {self.port}")
            print(f"Exception caught: {e}")

        return False

    def get_balance(self, account_number: int, pwd: str) -> AccountBalance:
        try:
            with MT5Session(self.host, self.port, account_number, pwd, self.server) as session:
                acc_info = session.account_info()
                print(acc_info)
                balance = AccountBalance(acc_info.currency,
                                         round_flt_to_4_decimal(acc_info.balance),
                                         round_flt_to_4_decimal(acc_info.credit),
                                         round_flt_to_4_decimal(acc_info.profit),
                                         round_flt_to_4_decimal(acc_info.equity),
                                         round_flt_to_4_decimal(acc_info.margin),
                                         round_flt_to_4_decimal(acc_info.margin_free),
                                         round_flt_to_4_decimal(acc_info.margin_level),
                                         round_flt_to_4_decimal(acc_info.margin_so_call),
                                         round_flt_to_4_decimal(acc_info.margin_so_so),
                                         round_flt_to_4_decimal(acc_info.margin_initial),
                                         round_flt_to_4_decimal(acc_info.margin_maintenance),
                                         round_flt_to_4_decimal(acc_info.assets),
                                         round_flt_to_4_decimal(acc_info.liabilities),
                                         round_flt_to_4_decimal(acc_info.commission_blocked),
                                         )
                return balance
        except MT5InvalidCreds as e:
            logger.error(f"[MT5|Invalid Creds], account_number: {account_number}, server: {self.server}, host: {self.host}, port: {self.port}")
            print(f"Invalid credentials: {e}")
        except Exception as e:
            logger.error(f"[MT5|Unknown Exception]")
            print(f"Exception caught: {e}")

        return False

    def determine_account_type(self, account_number: int, pwd: str) -> AccountType:
        try:
            with MT5Session(self.host, self.port, account_number, pwd, self.server) as session:
                symbols=session.symbols_get()
                for symbol in symbols:
                    if symbol.currency_profit == EURUSD.quote and symbol.currency_base == EURUSD.base:
                        print('determine_account_type Symbol: ', symbol)
                        if symbol.name == EURUSD.symbol_standard:
                            return AccountType.STANDARD
                        elif symbol.name == EURUSD.symbol_zero:
                            return AccountType.ZERO
                        elif symbol.name == EURUSD.symbol_mini:
                            return AccountType.MINI
                        else:
                            logger.error(f"[MT5|Symbol Info Failed], account_number: {account_number}, ")
                            return None
        except MT5InvalidCreds as e:
            logger.error(f"[MT5|Invalid Creds], account_number: {account_number}, server: {self.server}, host: {self.host}, port: {self.port}")
            print(f"determine_account_type Invalid credentials: {e}")
        except Exception as e:
            logger.error(f"[MT5|Unknown Exception]")
            print(f"determine_account_type Exception caught: {e}")

        return None


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
        if exc_type is not None:
            logger.error(f"[MT5|Exception], last_error: {self.mt5.last_error()}")
            print(f"Exception caught: {exc_value}")
        self.mt5.shutdown()

server_env = os.getenv("MT5_SERVER")
host_env = os.getenv("MT5_HOST")
port_env = os.getenv("MT5_PORT")

def is_none_or_empty(string):
    return string is None or string.strip() == ""

if is_none_or_empty(server_env) or is_none_or_empty(host_env) or is_none_or_empty(port_env):
    raise ValueError("MT5_HOST, MT5_PORT, and MT5_SERVER environment variables must be set")

mt5service = MT5Service(server_env, host_env, int(port_env))

# if __name__ == '__main__':
#     mt5service.determine_account_type(129404, '/0q2M/Jy')
#     mt5service.get_balance(130541, 'I?F3.pq1')


# currency_base='EUR'
# currency_profit='USD'
# SymbolInfo(custom=False, chart_mode=0, select=True, visible=True, session_deals=0, session_buy_orders=0, session_sell_orders=0, volume=0, volumehigh=0, volumelow=0, time=1746564222, digits=5, spread=16, spread_float=True, ticks_bookdepth=0, trade_calc_mode=0, trade_mode=4, start_time=0, expiration_time=0, trade_stops_level=0, trade_freeze_level=0, trade_exemode=2, swap_mode=1, swap_rollover3days=3, margin_hedged_use_leg=False, expiration_mode=15, filling_mode=3, order_mode=63, order_gtc_mode=0, option_mode=0, option_right=0, bid=1.13681, bidhigh=1.13696, bidlow=1.12792, ask=1.13697, askhigh=1.13712, asklow=1.12807, last=0.0, lasthigh=0.0, lastlow=0.0, volume_real=0.0, volumehigh_real=0.0, volumelow_real=0.0, option_strike=0.0, point=1e-05, trade_tick_value=1.0, trade_tick_value_profit=1.0, trade_tick_value_loss=1.0, trade_tick_size=1e-05, trade_contract_size=100000.0, trade_accrued_interest=0.0, trade_face_value=0.0, trade_liquidity_rate=0.0, volume_min=0.01, volume_max=10.0, volume_step=0.01, volume_limit=0.0, swap_long=-10.83, swap_short=3.92, margin_initial=0.0, margin_maintenance=0.0, session_volume=0.0, session_turnover=0.0, session_interest=0.0, session_buy_orders_volume=0.0, session_sell_orders_volume=0.0, session_open=1.13124, session_close=1.13124, session_aw=0.0, session_price_settlement=0.0, session_price_limit_min=0.0, session_price_limit_max=0.0, margin_hedged=0.0, price_change=0.4924, price_volatility=0.0, price_theoretical=0.0, price_greeks_delta=0.0, price_greeks_theta=0.0, price_greeks_gamma=0.0, price_greeks_vega=0.0, price_greeks_rho=0.0, price_greeks_omega=0.0, price_sensitivity=0.0, basis='', category='', currency_profit='USD', currency_margin='EUR', bank='', description='Euro vs US Dollar', exchange='', formula='', isin='', name='EURUSDz', page='', path='ForexZ\\Tier 1\\EURUSDz')
