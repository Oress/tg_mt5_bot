from enum import Enum

from sqlalchemy.orm.session import Session
from telegram import Update
from telegram.ext import ContextTypes

from tg_server.constants.UserRole import UserRole
from tg_server.database.db import engine
from tg_server.services.UserService import UserService
from telegram import ReplyKeyboardMarkup, KeyboardButton

class KeyboardButtons(str, Enum):
    BUY = "Купить"
    SELL = "Продать"

    PREVIEW_EDIT = "Изменить"
    PLACE_ORDER = "Разместить ордер"

    HEDGE = 'Хежд'
    BALANCE = 'Баланс'

    INVITE_USER= 'Пригласить пользователя'
    INVITE_ADMIN= 'Пригласить админа'



user_main_menu_keyboard = [
    [KeyboardButton(KeyboardButtons.HEDGE), KeyboardButton(KeyboardButtons.BALANCE)]
]

admin_main_menu_keyboard = [
    user_main_menu_keyboard[0],
    [KeyboardButton(KeyboardButtons.INVITE_USER), KeyboardButton(KeyboardButtons.INVITE_ADMIN)],
]

user_main_menu_markup = ReplyKeyboardMarkup(user_main_menu_keyboard, one_time_keyboard=True, resize_keyboard=True)
admin_main_menu_markup = ReplyKeyboardMarkup(admin_main_menu_keyboard, one_time_keyboard=True, resize_keyboard=True)


async def send_main_menu(update: Update, context: ContextTypes.DEFAULT_TYPE):
    user_id = update.effective_user.id
    with Session(engine) as session:
        user_service = UserService(session)
        user = user_service.get_user_by_id(user_id)
        user_role = user.role

    await context.bot.send_message(chat_id=update.effective_chat.id,
                                   text="Главное меню:",
                                   reply_markup=user_main_menu_markup if user_role == UserRole.USER else admin_main_menu_markup)

