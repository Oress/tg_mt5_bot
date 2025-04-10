from enum import Enum

class ConversationStates(str, Enum):
    MAIN_MENU = 'MAIN_MENU'

class InviteUserConvStates(str, Enum):
    PROMPT_USER = 'PROMPT_USER'
    PROMPT_CREDENTIALS = 'PROMPT_CREDENTIALS'

