import logging

from telegram import InlineKeyboardMarkup, Update
from telegram.ext import ContextTypes

from tg_server.handlers.commons import main_menu_decorator, UD_KEY_MENU_MSG_ID, add_message_to_delete

logger = logging.getLogger(__name__)

help_msg = (
    f"/start - Запустить бота и показать главное меню"
)

async def help_command_user(update: Update, context: ContextTypes.DEFAULT_TYPE):
    logger.info(f"[Help][authorized][chat_id: {update.effective_chat.id}][username: {update.effective_user.username}][user_id: {update.effective_user.id}]")
    await update.message.delete()

    view_msg_id = context.user_data.get(UD_KEY_MENU_MSG_ID)
    if view_msg_id is not None:
        await context.bot.edit_message_text(
            chat_id=update.effective_chat.id,
            message_id=view_msg_id,
            text=help_msg,
            reply_markup=InlineKeyboardMarkup(main_menu_decorator([])),
        )
    else:
        msg = await update.effective_chat.send_message(help_msg)
        add_message_to_delete([msg], context)

async def help_command_unauthorized(update: Update, context: ContextTypes.DEFAULT_TYPE):
    logger.warning(f"[Help][unauthorized][chat_id: {update.effective_chat.id}][username: {update.effective_user.username}][user_id: {update.effective_user.id}]")
    await update.message.delete()
    await update.message.reply_text(
        f"Этот бот доступен только для авторизованных пользователей. Пожалуйста, получите приглашение, чтобы использовать его.")
