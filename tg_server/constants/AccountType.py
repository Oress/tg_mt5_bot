from enum import Enum

class AccountType(str, Enum):
    ZERO = "ZERO" # z suffix
    MINI = "MINI" # m suffix
    STANDARD = "STANDARD" # no suffix