# BTC/USDT. BTC - base, USDT - quote.
from typing import Optional

class Instrument:
    def __init__(self, symbol: str, label: str, base: str, base_label: str, quote: str, quote_label: str, ):
        self.symbol = symbol
        self.label = label
        self.base = base
        self.base_label = base_label
        self.quote = quote
        self.quote_label = quote_label


EURUSD = Instrument("EURUSDm", "EUR/USD", "EUR", "евро", "USD", "доллар")

available_instruments = [EURUSD]

def get_instrument_by_symbol(symbol: str) -> Optional[Instrument]:
    for instrument in available_instruments:
        if instrument.symbol == symbol:
            return instrument
    return None

def get_instrument_by_label(label: str) -> Optional[Instrument]:
    for instrument in available_instruments:
        if instrument.label == label:
            return instrument
    return None