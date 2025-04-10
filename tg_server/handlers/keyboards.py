from enum import Enum

from telegram import ReplyKeyboardMarkup, KeyboardButton

class KeyboardButtons(str, Enum):
    HEDGE = 'Hedge'
    BALANCE = 'Balance'

user_main_menu_keyboard = [
    [KeyboardButton(KeyboardButtons.HEDGE), KeyboardButton(KeyboardButtons.BALANCE)]
]

admin_main_menu_keyboard = [
    user_main_menu_keyboard[0],
    [KeyboardButton("Invite User")]
]

user_main_menu_markup = ReplyKeyboardMarkup(user_main_menu_keyboard, one_time_keyboard=True, resize_keyboard=True)
admin_main_menu_markup = ReplyKeyboardMarkup(admin_main_menu_keyboard, one_time_keyboard=True, resize_keyboard=True)
