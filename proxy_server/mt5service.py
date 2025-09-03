import logging
import os
from dataclasses import dataclass
from decimal import Decimal
from typing import List

from mt5linux import MetaTrader5
from AccountType import AccountType
from Instruments import EURUSD

logger = logging.getLogger(__name__)

class MT5InvalidCreds(Exception):
    def __init__(self, message):
        super().__init__(message)

class MyOrderSendResult:
    def __init__(self, retcode, deal, order, volume, price, bid, ask, comment, request_id, retcode_external, request):
        self.retcode = retcode
        self.deal = deal
        self.order = order
        self.volume = volume
        self.price = price
        self.bid = bid
        self.ask = ask
        self.comment = comment
        self.request_id = request_id
        self.retcode_external = retcode_external
        self.request = request

class MyTradeRequest:
    def __init__(self, action, magic, order, symbol, volume, price, stoplimit, sl, tp, deviation, type, type_filling, type_time, expiration, comment, position, position_by):
        self.action = action
        self.magic = magic
        self.order = order
        self.symbol = symbol
        self.volume = volume
        self.price = price
        self.stoplimit = stoplimit
        self.sl = sl
        self.tp = tp
        self.deviation = deviation
        self.type = type
        self.type_filling = type_filling
        self.type_time = type_time
        self.expiration = expiration
        self.comment = comment
        self.position = position
        self.position_by = position_by

@dataclass
class MyTradePosition:
    def __init__(self, ticket, time, time_msc, time_update, time_update_msc, type, magic, identifier, reason, volume, 
                 price_open, sl, tp, price_current, swap, profit, symbol, comment, external_id):
        self.ticket = ticket
        self.time = time
        self.time_msc = time_msc 
        self.time_update = time_update
        self.time_update_msc = time_update_msc
        self.type = type
        self.magic = magic
        self.identifier = identifier
        self.reason = reason
        self.volume = volume
        self.price_open = price_open
        self.sl = sl
        self.tp = tp
        self.price_current = price_current
        self.swap = swap
        self.profit = profit
        self.symbol = symbol
        self.comment = comment
        self.external_id = external_id


@dataclass
class MyTick:
    def __init__(self, time, bid, ask, last, volume, time_msc, flags, volume_real):
        self.time = time
        self.bid = bid
        self.ask = ask
        self.last = last
        self.volume = volume
        self.time_msc = time_msc
        self.flags = flags
        self.volume_real = volume_real

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
class TradeDeal:
    ticket: Decimal
    order: Decimal
    time: Decimal
    time_msc: Decimal
    type: Decimal
    entry: Decimal
    magic: Decimal
    position_id: Decimal
    reason: Decimal
    volume: Decimal
    price: Decimal
    commission: Decimal
    swap: Decimal
    profit: Decimal
    fee: Decimal
    symbol: str
    comment: str
    external_id: str

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

    def history_deals(self, account_number: int, pwd: str, ticket: int) -> bool:
        try:
            with MT5Session(self.host, self.port, account_number, pwd, self.server) as session:
                # history_items=session.history_deals_get(position=position_id)
                history_items=session.history_deals_get(ticket=ticket)

                result = []
                for hi in history_items:
                    result.append(TradeDeal(
                        ticket=hi.ticket,
                        order=hi.order,
                        time=hi.time,
                        time_msc=hi.time_msc,
                        type=hi.type,
                        entry=hi.entry,
                        magic=hi.magic,
                        position_id=hi.position_id,
                        reason=hi.reason,
                        volume=hi.volume,
                        price=hi.price,
                        commission=hi.commission,
                        swap=hi.swap,
                        profit=hi.profit,
                        fee=hi.fee,
                        symbol=hi.symbol,
                        comment=hi.comment,
                        external_id=hi.external_id
                    ))
                return result
        except MT5InvalidCreds as e:
            logger.error(f"[MT5|Invalid Creds], account_number: {account_number}, server: {self.server}, host: {self.host}, port: {self.port}")
            print(f"Invalid credentials: {e}")
        except Exception as e:
            logger.error(f"[MT5|Unknown Exception]")
            print(f"Exception caught: {e}")

        return []

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

    def get_info_tick(self, account_number: int, pwd: str, symbol: str) -> Decimal:
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

                    return MyTick(time=info_tick.time,
                                  bid=info_tick.bid,
                                  ask=info_tick.ask,
                                  last=info_tick.last,
                                  volume=info_tick.volume,
                                  time_msc=info_tick.time_msc,
                                  flags=info_tick.flags,
                                  volume_real=info_tick.volume_real)
        except MT5InvalidCreds as e:
            logger.error(f"[MT5|Invalid Creds], account_number: {account_number}, server: {self.server}, host: {self.host}, port: {self.port}")
            print(f"Invalid credentials: {e}")
        except Exception as e:
            logger.error(f"[MT5|Unknown Exception]")
            print(f"Exception caught: {e}")

        return Decimal(0)

    def positions_get_all(self, account_number: int, pwd: str) -> List[MyTradePosition]:
            logger.info(f"[MT5|orders_get] account_number: {account_number}")
            selected = []
            try:
                with MT5Session(self.host, self.port, account_number, pwd, self.server) as session:
                    selected = session.positions_get()
                    if selected is None:
                        logger.warning(f"[MT5|orders_get], No orders for the account_number: {account_number}")
                        return []

                    positions = []
                    for pos in selected:
                        positions.append(MyTradePosition(
                            ticket=pos.ticket,
                            time=pos.time,
                            time_msc=pos.time_msc,
                            time_update=pos.time_update,
                            time_update_msc=pos.time_update_msc,
                            type=pos.type,
                            magic=pos.magic,
                            identifier=pos.identifier,
                            reason=pos.reason,
                            volume=pos.volume,
                            price_open=pos.price_open,
                            sl=pos.sl,
                            tp=pos.tp,
                            price_current=pos.price_current,
                            swap=pos.swap,
                            profit=pos.profit,
                            symbol=pos.symbol,
                            comment=pos.comment,
                            external_id=pos.external_id
                        ))
                    print(positions)
                    return positions

            except MT5InvalidCreds as e:
                logger.error(f"[MT5|Invalid Creds], account_number: {account_number}, server: {self.server}, host: {self.host}, port: {self.port}")
                print(f"Invalid credentials: {e}")
            except Exception as e:
                logger.error(f"[MT5|Unknown Exception]")
                print(f"Exception caught: {e}")

            return selected

    def positions_get(self, account_number: int, pwd: str, symbol: str) -> List[MyTradePosition]:
        logger.info(f"[MT5|orders_get] account_number: {account_number}, symbol: {symbol}")
        selected = []
        try:
            with MT5Session(self.host, self.port, account_number, pwd, self.server) as session:
                selected = session.positions_get(symbol=symbol)
                if selected is None:
                    logger.warning(f"[MT5|orders_get], No orders for the account_number: {account_number}, symbol: {symbol}")
                    return []

                positions = []
                for pos in selected:
                    positions.append(MyTradePosition(
                        ticket=pos.ticket,
                        time=pos.time,
                        time_msc=pos.time_msc,
                        time_update=pos.time_update,
                        time_update_msc=pos.time_update_msc,
                        type=pos.type,
                        magic=pos.magic,
                        identifier=pos.identifier,
                        reason=pos.reason,
                        volume=pos.volume,
                        price_open=pos.price_open,
                        sl=pos.sl,
                        tp=pos.tp,
                        price_current=pos.price_current,
                        swap=pos.swap,
                        profit=pos.profit,
                        symbol=pos.symbol,
                        comment=pos.comment,
                        external_id=pos.external_id
                    ))
                print(positions)
                return positions

        except MT5InvalidCreds as e:
            logger.error(f"[MT5|Invalid Creds], account_number: {account_number}, server: {self.server}, host: {self.host}, port: {self.port}")
            print(f"Invalid credentials: {e}")
        except Exception as e:
            logger.error(f"[MT5|Unknown Exception]")
            print(f"Exception caught: {e}")

        return selected

    def place_short_order(self, account_number: int, pwd: str, position_id: int, symbol: str, volume: Decimal):
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
                    "comment": f"TGBot place short",
                    "price": info_tick.bid,
                    # "deviation": deviation,
                    # "type_time": MetaTrader5.ORDER_TIME_GTC,
                    # "type_filling": MetaTrader5.ORDER_FILLING_RETURN,
                }

                if position_id is not None:
                    request["position"] = position_id

                order_result = session.order_send(request)
                
                my_order_result = MyOrderSendResult(
                    retcode=order_result.retcode,
                    deal=order_result.deal,
                    order=order_result.order,
                    volume=order_result.volume,
                    price=order_result.price,
                    bid=order_result.bid,
                    ask=order_result.ask,
                    comment=order_result.comment,
                    request_id=order_result.request_id,
                    retcode_external=order_result.retcode_external,
                    request=MyTradeRequest(
                        action=order_result.request.action,
                        magic=order_result.request.magic,
                        order=order_result.request.order,
                        symbol=order_result.request.symbol,
                        volume=order_result.request.volume,
                        price=order_result.request.price,
                        stoplimit=order_result.request.stoplimit,
                        sl=order_result.request.sl,
                        tp=order_result.request.tp,
                        deviation=order_result.request.deviation,
                        type=order_result.request.type,
                        type_filling=order_result.request.type_filling,
                        type_time=order_result.request.type_time,
                        expiration=order_result.request.expiration,
                        comment=order_result.request.comment,
                        position=order_result.request.position,
                        position_by=order_result.request.position_by
                    )
                )

                logger.info(f"[MT5|place_short_order] order_result: {my_order_result}")
                logger.info(f"[MT5|place_short_order] error: {session.last_error()}")
                return my_order_result
        except MT5InvalidCreds as e:
            logger.error(f"[MT5|Invalid Creds], account_number: {account_number}, server: {self.server}, host: {self.host}, port: {self.port}")
            print(f"Invalid credentials: {e}")
        except Exception as e:
            logger.error(f"[MT5|Unknown Exception]")
            print(f"Exception caught: {e}")

        return None

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
                    "price": info_tick.ask,
                    # "deviation": deviation,
                    # "magic": ea_magic_number,
                    "comment": "TGBot place long",
                    "type_time": MetaTrader5.ORDER_TIME_GTC,
                    "type_filling": MetaTrader5.ORDER_FILLING_FOK,
                }

                if position_id is not None:
                    close_request["position"] = position_id

                order_result = session.order_send(close_request)
                my_order_result = MyOrderSendResult(
                    retcode=order_result.retcode,
                    deal=order_result.deal,
                    order=order_result.order,
                    volume=order_result.volume,
                    price=order_result.price,
                    bid=order_result.bid,
                    ask=order_result.ask,
                    comment=order_result.comment,
                    request_id=order_result.request_id,
                    retcode_external=order_result.retcode_external,
                    request=MyTradeRequest(
                        action=order_result.request.action,
                        magic=order_result.request.magic,
                        order=order_result.request.order,
                        symbol=order_result.request.symbol,
                        volume=order_result.request.volume,
                        price=order_result.request.price,
                        stoplimit=order_result.request.stoplimit,
                        sl=order_result.request.sl,
                        tp=order_result.request.tp,
                        deviation=order_result.request.deviation,
                        type=order_result.request.type,
                        type_filling=order_result.request.type_filling,
                        type_time=order_result.request.type_time,
                        expiration=order_result.request.expiration,
                        comment=order_result.request.comment,
                        position=order_result.request.position,
                        position_by=order_result.request.position_by
                    )
                )
                logger.info(f"[MT5|close_short_order] order_result: {order_result}")
                logger.info(f"[MT5|close_short_order] error: {session.last_error()}")
                return my_order_result
        except MT5InvalidCreds as e:
            logger.error(f"[MT5|Invalid Creds], account_number: {account_number}, server: {self.server}, host: {self.host}, port: {self.port}")
            print(f"Invalid credentials: {e}")
        except Exception as e:
            logger.error(f"[MT5|Unknown Exception]")
            print(f"Exception caught: {e}")

        return None

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
                    volume_min=(sym_info.volume_min),
                    volume_step=(sym_info.volume_step),
                    volume_max=(sym_info.volume_max),
                    volume_limit=(sym_info.volume_limit),
                    trade_contract_size=(sym_info.trade_contract_size)
                )
                return sym_info_model
        except MT5InvalidCreds as e:
            logger.error(f"[MT5|Invalid Creds], account_number: {account_number}, server: {self.server}, host: {self.host}, port: {self.port}")
            print(f"Invalid credentials: {e}")
        except Exception as e:
            logger.error(f"[MT5|Unknown Exception], account_number: {account_number}, server: {self.server}, host: {self.host}, port: {self.port}")
            print(f"Exception caught: {e}")

        return None

    def get_balance(self, account_number: int, pwd: str) -> AccountBalance:
        try:
            with MT5Session(self.host, self.port, account_number, pwd, self.server) as session:
                acc_info = session.account_info()
                print(acc_info)
                balance = AccountBalance(acc_info.currency,
                                         (acc_info.balance),
                                         (acc_info.credit),
                                         (acc_info.profit),
                                         (acc_info.equity),
                                         (acc_info.margin),
                                         (acc_info.margin_free),
                                         (acc_info.margin_level),
                                         (acc_info.margin_so_call),
                                         (acc_info.margin_so_so),
                                         (acc_info.margin_initial),
                                         (acc_info.margin_maintenance),
                                         (acc_info.assets),
                                         (acc_info.liabilities),
                                         (acc_info.commission_blocked),
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

# if __name__ == '__main__':
#     mt5service.determine_account_type(129404, '/0q2M/Jy')
#     mt5service.get_balance(130541, 'I?F3.pq1')


# currency_base='EUR'
# currency_profit='USD'
# SymbolInfo(custom=False, chart_mode=0, select=True, visible=True, session_deals=0, session_buy_orders=0, session_sell_orders=0, volume=0, volumehigh=0, volumelow=0, time=1746564222, digits=5, spread=16, spread_float=True, ticks_bookdepth=0, trade_calc_mode=0, trade_mode=4, start_time=0, expiration_time=0, trade_stops_level=0, trade_freeze_level=0, trade_exemode=2, swap_mode=1, swap_rollover3days=3, margin_hedged_use_leg=False, expiration_mode=15, filling_mode=3, order_mode=63, order_gtc_mode=0, option_mode=0, option_right=0, bid=1.13681, bidhigh=1.13696, bidlow=1.12792, ask=1.13697, askhigh=1.13712, asklow=1.12807, last=0.0, lasthigh=0.0, lastlow=0.0, volume_real=0.0, volumehigh_real=0.0, volumelow_real=0.0, option_strike=0.0, point=1e-05, trade_tick_value=1.0, trade_tick_value_profit=1.0, trade_tick_value_loss=1.0, trade_tick_size=1e-05, trade_contract_size=100000.0, trade_accrued_interest=0.0, trade_face_value=0.0, trade_liquidity_rate=0.0, volume_min=0.01, volume_max=10.0, volume_step=0.01, volume_limit=0.0, swap_long=-10.83, swap_short=3.92, margin_initial=0.0, margin_maintenance=0.0, session_volume=0.0, session_turnover=0.0, session_interest=0.0, session_buy_orders_volume=0.0, session_sell_orders_volume=0.0, session_open=1.13124, session_close=1.13124, session_aw=0.0, session_price_settlement=0.0, session_price_limit_min=0.0, session_price_limit_max=0.0, margin_hedged=0.0, price_change=0.4924, price_volatility=0.0, price_theoretical=0.0, price_greeks_delta=0.0, price_greeks_theta=0.0, price_greeks_gamma=0.0, price_greeks_vega=0.0, price_greeks_rho=0.0, price_greeks_omega=0.0, price_sensitivity=0.0, basis='', category='', currency_profit='USD', currency_margin='EUR', bank='', description='Euro vs US Dollar', exchange='', formula='', isin='', name='EURUSDz', page='', path='ForexZ\\Tier 1\\EURUSDz')
