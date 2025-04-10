from sqlalchemy.orm.session import Session
from telegram import Update
from telegram.ext import ContextTypes

from tg_server.constants.UserRole import UserRole
from tg_server.database.db import engine
from tg_server.handlers.keyboards import user_main_menu_markup, admin_main_menu_markup
from tg_server.services.UserService import UserService


async def send_main_menu(update: Update, context: ContextTypes.DEFAULT_TYPE):
    user_id = update.effective_user.id
    with Session(engine) as session:
        user_service = UserService(session)
        user = user_service.get_user_by_id(user_id)
        user_role = user.role

    await context.bot.send_message(chat_id=update.effective_chat.id,
                                   text="Main menu:",
                                   reply_markup=user_main_menu_markup if user_role == UserRole.USER else admin_main_menu_markup)

