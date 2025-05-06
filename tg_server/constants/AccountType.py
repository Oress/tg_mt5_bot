from enum import Enum

class AccountType(str, Enum):
    ZERO = "ZERO" # EURUSDz
    MINI = "MINI" # EURUSDm
    STANDARD = "STANDARD" # EURUSD