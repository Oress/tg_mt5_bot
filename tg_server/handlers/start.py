import logging

from sqlalchemy.orm.session import Session
from telegram import Update
from telegram.ext import ConversationHandler, ContextTypes

from tg_server.database.db import engine
from tg_server.handlers.commons import send_main_menu, UD_KEY_MENU_MSG_ID
from tg_server.handlers.states import ConversationStates
from tg_server.services.InviteLinkService import InviteLinkService
from tg_server.services.UserService import UserService

logger = logging.getLogger(__name__)


async def start_command(update: Update, context: ContextTypes.DEFAULT_TYPE):
    logger.info('[Start Command] Start command received')
    await context.bot.delete_message(chat_id=update.effective_chat.id, message_id=update.effective_message.message_id)
    with Session(engine) as session:
        user_service = UserService(session)
        user_id = update.effective_user.id
        user = user_service.get_user_by_id(user_id)

        if user is not None:
            logger.info(f"[Start Registered User], chat_id: {update.effective_chat.id}, username: {update.effective_user.username}, user_id {update.effective_user.id}, message: {update.message.text}")

            if context.user_data.get(UD_KEY_MENU_MSG_ID) is not None:
                try:
                    await context.bot.delete_message(chat_id=update.effective_chat.id, message_id=context.user_data[UD_KEY_MENU_MSG_ID])
                except Exception as e:
                    logger.error(f"[Start Registered User] Error deleting message: {e}")

            menu_msg = await send_main_menu(update, context)
            context.user_data[UD_KEY_MENU_MSG_ID] = menu_msg.id
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
            user_service.create_user(user_id, link.role_on_accept, link.created_by, link.mt5_acc_number, link.mt5_acc_pwd_enc)
            session.commit()
            await send_main_menu(update, context)
            return ConversationStates.MAIN_MENU
        else:
            logger.warning(f"[Start Invalid Link], chat_id: {update.effective_chat.id}, username: {update.effective_user.username}, user_id {update.effective_user.id}, message: {update.message.text}")
            await context.bot.send_message(chat_id=update.effective_chat.id, text="Недействительное приглашение.")
            return ConversationHandler.END
