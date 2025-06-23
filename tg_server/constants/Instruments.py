# BTC/USDT. BTC - base, USDT - quote.
from enum import Enum
from typing import Optional

class InstrumentMode(str, Enum):
    SHORT = "short"
    LONG = "long"

class Instrument:
    # symbol_standard will be used as a common identifier
    def __init__(self, symbol_zero: str, symbol_mini: str, symbol_standard: str, label: str, base: str, quote: str, mode: InstrumentMode):
        self.symbol_zero = symbol_zero
        self.symbol_mini = symbol_mini
        self.symbol_standard = symbol_standard
        self.label = label
        self.base = base
        self.quote = quote
        self.mode = mode
    
    def currency_for_hedge(self) -> str:
        if self.mode == InstrumentMode.SHORT:
            return self.base
        elif self.mode == InstrumentMode.LONG:
            return self.quote
        else:
            raise ValueError(f"Unknown mode: {self.mode}")

EURUSD = Instrument("EURUSDz", "EURUSDm", "EURUSD", "EUR/USD", "EUR", "USD", InstrumentMode.SHORT)
USDCAD = Instrument("USDCADz", "USDCADm", "USDCAD", "USD/CAD", "USD", "CAD", InstrumentMode.LONG)

available_instruments = [EURUSD, USDCAD]

def get_instrument_by_symbol(symbol: str) -> Optional[Instrument]:
    for instrument in available_instruments:
        if instrument.symbol_standard == symbol or instrument.symbol_zero == symbol or instrument.symbol_mini == symbol:
            return instrument
    return None
