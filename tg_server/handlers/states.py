from enum import Enum

class ConversationStates(str, Enum):
    MAIN_MENU = 'MAIN_MENU'

class BalanceConvStates(str, Enum):
    BALANCE_OPENED = 'BALANCE_OPENED'

class InviteUserConvStates(str, Enum):
    PROMPT_USER = 'PROMPT_USER'
    PROMPT_CREDENTIALS = 'PROMPT_CREDENTIALS'
    RESULT = 'RESULT'

class UnblockUserConvStates(str, Enum):
    PROMPT_USER = 'PROMPT_USER'
    PROMPT_CREDENTIALS = 'PROMPT_CREDENTIALS'
    RESULT = 'RESULT'

class BlockUserConvStates(str, Enum):
    PROMPT_USER = 'PROMPT_USER'
    PROMPT_REASON = 'PROMPT_REASON'
    RESULT = 'RESULT'

class ChangeCredsConvStates(str, Enum):
    PROMPT_USER = 'PROMPT_USER'
    PROMPT_CREDENTIALS = 'PROMPT_CREDENTIALS'
    RESULT = 'RESULT'

class InviteAdminConvStates(str, Enum):
    PROMPT_USER = 'PROMPT_USER'
    RESULT = 'RESULT'


class HedgeConvStates(str, Enum):
    SELECT_OPERATION = 'SELECT_OPERATION'
    SELECT_INSTRUMENT = 'SELECT_INSTRUMENT'
    SELECT_VOLUME = 'SELECT_VOLUME'
    SELECT_CITY = 'SELECT_CITY'
    PREVIEW = 'PREVIEW'
    RESULT = 'RESULT'

