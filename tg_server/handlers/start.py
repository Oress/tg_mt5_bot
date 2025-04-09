import logging

from telegram import Update
from telegram.ext import CommandHandler, ContextTypes

from tg_server.services.InviteLinkService import invite_link_service

logger = logging.getLogger(__name__)

async def start(update: Update, context: ContextTypes.DEFAULT_TYPE):
    if len(context.args) == 0:
        await context.bot.send_message(chat_id=update.effective_chat.id, text="Please use the invite link.")
        return

    user_id = update.effective_user.id
    invite_code = context.args[0]

    if invite_link_service.validate_invite_link(user_id, invite_code) is True:
        await context.bot.send_message(chat_id=update.effective_chat.id, text="Welcome!")
    else:
        await context.bot.send_message(chat_id=update.effective_chat.id, text="Invalid invite link.")

start_handler = CommandHandler('start', start)

