"""Create the documented MT5 startup configuration; never write account secrets."""
import os
from pathlib import Path
import re


def configure(directory, symbol):
    if not re.fullmatch(r"[A-Za-z0-9_.#-]{1,64}", symbol):
        raise ValueError("MT5_FEED_SYMBOL must be one broker symbol, without whitespace")
    directory = Path(directory)
    directory.mkdir(parents=True, exist_ok=True)
    config = ("[Common]\r\nKeepPrivate=0\r\n"
              "[Experts]\r\nEnabled=1\r\nAllowLiveTrading=1\r\nAllowDllImport=1\r\n"
              "Account=0\r\nProfile=0\r\n"
              "[StartUp]\r\nExpert=GatewayFeed.ex5\r\n"
              f"Symbol={symbol}\r\nPeriod=M1\r\n")
    destination = directory / "gateway.ini"
    temporary = destination.with_suffix(".tmp")
    temporary.write_text(config, encoding="utf-16", newline="")
    temporary.replace(destination)
    return destination


if __name__ == "__main__":
    configure("/state/terminal", os.environ.get("MT5_FEED_SYMBOL", "EURUSD"))
