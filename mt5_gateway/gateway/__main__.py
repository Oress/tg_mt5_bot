import asyncio
import logging
import os

import MetaTrader5

from .server import Gateway
from .terminal import Terminal
from .permissions import enable_python_trading, terminal_running


def main():
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
    terminal = Terminal(MetaTrader5, os.environ["MT5_TERMINAL_PATH"], os.environ.get("MT5_SERVER", ""),
                        os.environ["MT5_ORDER_JOURNAL"], os.environ.get("MT5_ALLOW_TRADING") == "1",
                        configure_permissions=enable_python_trading, is_running=terminal_running)
    gateway = Gateway(terminal, os.environ.get("MT5_GATEWAY_TOKEN", ""),
                      feed_symbol=os.environ.get("MT5_FEED_SYMBOL", "EURUSD"))
    asyncio.run(gateway.run())


if __name__ == "__main__":
    main()
