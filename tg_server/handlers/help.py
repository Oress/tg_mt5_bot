import logging

from telegram.ext import ContextTypes
from telegram import helpers, Update, ReplyKeyboardRemove, ReplyKeyboardMarkup, KeyboardButton

from tg_server.handlers.commons import send_main_menu

logger = logging.getLogger(__name__)

help_msg = (
    f"/start - Запустить бота и показать главное меню\n"
    # f"/settings - Show settings\n"
    # f"/feedback - Send feedback\n"
    # f"/support - Contact support\n"
    # f"/about - About the bot\n"

    # f"/admin - Admin commands\n"
    # f"/invite - Invite a new user\n"
    # f"/admin_help - Admin help\n"
)


# async def help_command_admin(update: Update, context: ContextTypes.DEFAULT_TYPE):
#     admin_help_msg = \
#         (
#                 help_msg
#                 + "\nКоманды админа:\n"
#                   "/inviteuser - Пригласить пользователя\n"
#                   "/inviteadmin - Пригласить администратора\n"
#         )
#     await update.message.reply_text(admin_help_msg)
#     await send_main_menu(update, context)


async def help_command_user(update: Update, context: ContextTypes.DEFAULT_TYPE):
    await update.message.reply_text(help_msg)
    await send_main_menu(update, context)


async def help_command_unauthorized(update: Update, context: ContextTypes.DEFAULT_TYPE):
    await update.message.reply_text(
        f"Этот бот доступен только для авторизованных пользователей. Пожалуйста, получите приглашение, чтобы использовать его.")
