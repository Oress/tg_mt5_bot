import logging
from decimal import Decimal, ROUND_HALF_UP
from typing import Union, Sequence, List

from sqlalchemy.orm.session import Session
from telegram import InlineKeyboardButton, InlineKeyboardMarkup
from telegram import Update, Message
from telegram.ext import ContextTypes

from tg_server.constants.UserRole import UserRole
from tg_server.database.db import engine
from tg_server.handlers.states import ConversationStates
from tg_server.services.UserService import UserService

UD_KEY_MENU_MSG_ID = "UD_KEY_MENU_MSG_ID"

logger = logging.getLogger(__name__)

class KeyboardButtons:
    def __init__(self, label, callback_data=None):
        self.label = label
        self.callback_data = callback_data

BUY = KeyboardButtons("🔼 Купить", "HEDGE_BUY")
SELL = KeyboardButtons("🔽 Продать", "HEDGE_SELL")
PREVIEW_EDIT = KeyboardButtons("✏️ Изменить", "HEDGE_EDIT")
PLACE_ORDER = KeyboardButtons("✅ Разместить ордер", "HEDGE_PLACE_ORDER")
HEDGE = KeyboardButtons('🏦 Хедж', "MM_HEDGE")
BALANCE = KeyboardButtons('⚖️ Баланс', "MM_BALANCE")
BLOCK_USER= KeyboardButtons('🔒 Заблокировать пользователя', "MM_BLOCK_USER")
UNBLOCK_USER= KeyboardButtons('🔓 Разблокировать пользователя', "MM_UNBLOCK_USER")
CHANGE_USER_ACCOUNT_CREDS= KeyboardButtons('✍️ Изменить учетные данные для пользователя', "MM_USER_CHNG_CREDS")
INVITE_USER= KeyboardButtons('📨 Пригласить пользователя', "MM_INVITE_USER")
INVITE_ADMIN= KeyboardButtons('📨 Пригласить админа', "MM_INVITE_ADMIN")
MAIN_MENU = KeyboardButtons("🏠 Главное меню", 'MAIN_MENU')
BLOCK_USER_SKIP_RSN = KeyboardButtons("Пропустить", 'BLCK_USR_SKIP_RSN')


user_main_menu_keyboard = [
    [
        InlineKeyboardButton(text=HEDGE.label, callback_data=HEDGE.callback_data),
        InlineKeyboardButton(text=BALANCE.label, callback_data=BALANCE.callback_data)
    ]
]

admin_main_menu_keyboard = [
    user_main_menu_keyboard[0],
    [
        InlineKeyboardButton(text=INVITE_USER.label, callback_data=INVITE_USER.callback_data),
        InlineKeyboardButton(text=INVITE_ADMIN.label, callback_data=INVITE_ADMIN.callback_data)
    ],
    [
        InlineKeyboardButton(text=CHANGE_USER_ACCOUNT_CREDS.label, callback_data=CHANGE_USER_ACCOUNT_CREDS.callback_data)
    ],
    [
        InlineKeyboardButton(text=BLOCK_USER.label, callback_data=BLOCK_USER.callback_data),
        InlineKeyboardButton(UNBLOCK_USER.label, callback_data=UNBLOCK_USER.callback_data)
    ],
]

user_main_menu_markup = InlineKeyboardMarkup(user_main_menu_keyboard)
admin_main_menu_markup = InlineKeyboardMarkup(admin_main_menu_keyboard)


def main_menu_decorator(menu: List[Sequence[Union[str, InlineKeyboardButton]]]):
    menu.append([InlineKeyboardButton(text=MAIN_MENU.label, callback_data=MAIN_MENU.callback_data)])
    return menu

async def send_main_menu(update: Update, context: ContextTypes.DEFAULT_TYPE):
    user_id = update.effective_user.id
    with Session(engine) as session:
        user_service = UserService(session)
        user = user_service.get_user_by_id(user_id)
        logger.info(f"[Send Main Menu] user_id: {user_id}, role: {user.role}")
        user_role = user.role

    return await context.bot.send_message(chat_id=update.effective_chat.id,
                                   text="Главное меню:",
                                   reply_markup=user_main_menu_markup if user_role == UserRole.USER else admin_main_menu_markup)

def remove_exponent(d):
    return d.quantize(Decimal(1)) if d == d.to_integral() else d.normalize()

reference = Decimal("0.0001")
def round_flt_to_4_decimal(flt: float) -> Decimal:
    return Decimal(flt).quantize(reference, rounding=ROUND_HALF_UP)

async def main_menu_command(update: Update, context: ContextTypes.DEFAULT_TYPE):
    await context.bot.delete_message(chat_id=update.effective_chat.id, message_id=update.effective_message.message_id)
    await purge_messages(update, context)

    if context.user_data.get(UD_KEY_MENU_MSG_ID) is not None:
        try:
            await context.bot.delete_message(chat_id=update.effective_chat.id, message_id=context.user_data[UD_KEY_MENU_MSG_ID])
        except Exception as e:
            logger.error(f"[Start Registered User] Error deleting message: {e}")

    menu_msg = await send_main_menu(update, context)
    context.user_data[UD_KEY_MENU_MSG_ID] = menu_msg.id
    return ConversationStates.MAIN_MENU


UD_KEY_MSGS_IDS_TO_DELETE = "UD_KEY_MSGS_IDS_TO_DELETE"
def add_message_to_delete(msgs: Sequence[Message], context: ContextTypes.DEFAULT_TYPE):
    messages = context.user_data.get(UD_KEY_MSGS_IDS_TO_DELETE, [])
    for msg in msgs:
        messages.append(msg.message_id)
    context.user_data[UD_KEY_MSGS_IDS_TO_DELETE] = messages

async def purge_messages(update: Update, context: ContextTypes.DEFAULT_TYPE):
    messages = context.user_data.get(UD_KEY_MSGS_IDS_TO_DELETE, [])
    if len(messages) == 0:
        return

    try:
        await context.bot.delete_messages(chat_id=update.effective_chat.id, message_ids=messages)
    except Exception as e:
        logger.error(f"[Purge Messages] Error deleting message: {e}")

    context.user_data[UD_KEY_MSGS_IDS_TO_DELETE] = []

async def main_menu_callback(update: Update, context: ContextTypes.DEFAULT_TYPE):
    query = update.callback_query
    await query.answer()

    await purge_messages(update, context)

    user_id = update.effective_user.id
    with Session(engine) as session:
        user_service = UserService(session)
        user = user_service.get_user_by_id(user_id)
        user_role = user.role

    await query.edit_message_text(
        text="Главное меню:",
        reply_markup=user_main_menu_markup if user_role == UserRole.USER else admin_main_menu_markup,
    )

    return ConversationStates.MAIN_MENU
