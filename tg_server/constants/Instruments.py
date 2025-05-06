# BTC/USDT. BTC - base, USDT - quote.
from typing import Optional

class Instrument:
    # symbol_standard will be used as a common identifier
    def __init__(self, symbol_zero: str, symbol_mini: str, symbol_standard: str, label: str, base: str, quote: str):
        self.symbol_zero = symbol_zero
        self.symbol_mini = symbol_mini
        self.symbol_standard = symbol_standard
        self.label = label
        self.base = base
        self.quote = quote

EURUSD = Instrument("EURUSDz", "EURUSDm", "EURUSD", "EUR/USD", "EUR", "USD")

available_instruments = [EURUSD]

def get_instrument_by_symbol(symbol: str) -> Optional[Instrument]:
    for instrument in available_instruments:
        if instrument.symbol_standard == symbol or instrument.symbol_zero == symbol or instrument.symbol_mini == symbol:
            return instrument
    return None
