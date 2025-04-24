from enum import Enum

class ConversationStates(str, Enum):
    MAIN_MENU = 'MAIN_MENU'

class InviteUserConvStates(str, Enum):
    PROMPT_USER = 'PROMPT_USER'
    PROMPT_CREDENTIALS = 'PROMPT_CREDENTIALS'

class ChangeCredsConvStates(str, Enum):
    PROMPT_USER = 'PROMPT_USER'
    PROMPT_CREDENTIALS = 'PROMPT_CREDENTIALS'

class InviteAdminConvStates(str, Enum):
    PROMPT_USER = 'PROMPT_USER'


class HedgeConvStates(str, Enum):
    SELECT_OPERATION = 'SELECT_OPERATION'
    SELECT_INSTRUMENT = 'SELECT_INSTRUMENT'
    SELECT_VOLUME = 'SELECT_VOLUME'
    SELECT_CITY = 'SELECT_CITY'
    PREVIEW = 'PREVIEW'

