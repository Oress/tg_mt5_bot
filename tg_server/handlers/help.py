import logging

from telegram.ext import ContextTypes
from telegram import helpers, Update, ReplyKeyboardRemove, ReplyKeyboardMarkup, KeyboardButton

from tg_server.handlers.commons import send_main_menu

logger = logging.getLogger(__name__)

help_msg = (
    f"/start - Запустить бота и показать главное меню\n"
)

async def help_command_user(update: Update, context: ContextTypes.DEFAULT_TYPE):
    await update.message.reply_text(help_msg)
    await send_main_menu(update, context)


async def help_command_unauthorized(update: Update, context: ContextTypes.DEFAULT_TYPE):
    await update.message.reply_text(
        f"Этот бот доступен только для авторизованных пользователей. Пожалуйста, получите приглашение, чтобы использовать его.")
