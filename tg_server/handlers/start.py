import logging

from sqlalchemy.orm.session import Session
from telegram import Update
from telegram.ext import ConversationHandler, ContextTypes

from tg_server.database.db import engine
from tg_server.handlers.commons import send_main_menu
from tg_server.handlers.states import ConversationStates
from tg_server.services.InviteLinkService import InviteLinkService
from tg_server.services.UserService import UserService

logger = logging.getLogger(__name__)

async def start_command(update: Update, context: ContextTypes.DEFAULT_TYPE):
    with Session(engine) as session:
        user_service = UserService(session)
        user_id = update.effective_user.id
        user = user_service.get_user_by_id(user_id)

        if user is not None:
            logger.info(f"[Start Registered User], chat_id: {update.effective_chat.id}, username: {update.effective_user.username}, user_id {update.effective_user.id}, message: {update.message.text}")
            await send_main_menu(update, context)
            return ConversationStates.MAIN_MENU

        if len(context.args) == 0:
            logger.warning(f"[Start No Link], chat_id: {update.effective_chat.id}, username: {update.effective_user.username}, user_id {update.effective_user.id}, message: {update.message.text}")
            await context.bot.send_message(chat_id=update.effective_chat.id, text="Please use the invite link.")
            return ConversationHandler.END

        invite_code = context.args[0]

        invite_link_service = InviteLinkService(session)

        link = invite_link_service.get_invite_link(user_id, invite_code)
        if link is not None:
            logger.info(f"[Start User Joined], chat_id: {update.effective_chat.id}, username: {update.effective_user.username}, user_id {update.effective_user.id}, message: {update.message.text}")
            user = user_service.create_user(user_id, link.role_on_accept, link.created_by, link.mt5_acc_number, link.mt5_acc_pwd_enc)
            await context.bot.send_message(chat_id=update.effective_chat.id,
                                           text="Welcome!")
            session.commit()
            await send_main_menu(update, context)
            return ConversationStates.MAIN_MENU
        else:
            logger.warning(f"[Start Invalid Link], chat_id: {update.effective_chat.id}, username: {update.effective_user.username}, user_id {update.effective_user.id}, message: {update.message.text}")
            await context.bot.send_message(chat_id=update.effective_chat.id, text="Invalid invite link.")
            return ConversationHandler.END



    return new_state

