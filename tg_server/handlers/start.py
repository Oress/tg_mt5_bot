import logging

from sqlalchemy.orm.session import Session
from telegram import Update
from telegram.ext import CommandHandler, ContextTypes

from tg_server.database.db import engine
from tg_server.services.InviteLinkService import InviteLinkService
from tg_server.services.UserService import UserService

logger = logging.getLogger(__name__)

async def start(update: Update, context: ContextTypes.DEFAULT_TYPE):
    if len(context.args) == 0:
        await context.bot.send_message(chat_id=update.effective_chat.id, text="Please use the invite link.")
        return

    user_id = update.effective_user.id
    invite_code = context.args[0]

    with Session(engine) as session:
        user_service = UserService(session)
        invite_link_service = InviteLinkService(session)

        link = invite_link_service.get_invite_link(user_id, invite_code)

        if link is not None:
            user_service.create_user(user_id, link.role_on_accept, link.created_by, link.mt5_acc_number, link.mt5_acc_pwd_enc)
            await context.bot.send_message(chat_id=update.effective_chat.id, text="Welcome!")
        else:
            await context.bot.send_message(chat_id=update.effective_chat.id, text="Invalid invite link.")

        session.commit()

start_handler = CommandHandler('start', start)

