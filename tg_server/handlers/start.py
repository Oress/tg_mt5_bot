import logging

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

    with engine.connect() as conn:
        user_service = UserService(conn)
        invite_link_service = InviteLinkService(conn)

        link = invite_link_service.get_invite_link(user_id, invite_code)
        if link is not None:
            # TODO: Replace with actual invited_by user ID
            user_service.register_user(user_id, None)
            await context.bot.send_message(chat_id=update.effective_chat.id, text="Welcome!")
        else:
            await context.bot.send_message(chat_id=update.effective_chat.id, text="Invalid invite link.")

        conn.commit()

start_handler = CommandHandler('start', start)

