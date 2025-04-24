import logging
from datetime import datetime

from sqlalchemy.orm.session import Session
from telegram import helpers, Update
from telegram.constants import ParseMode
from telegram.ext import ContextTypes

from tg_server.database.db import engine
from tg_server.handlers.commons import send_main_menu
from tg_server.handlers.states import ConversationStates
from tg_server.mt5.mt5service import mt5service
from tg_server.security.encryptor import pwd_encryptor
from tg_server.services.UserService import UserService

logger = logging.getLogger(__name__)

BALANCE_PREFIX_MD2 = "*\[Баланс\]*\n"

async def balance_command(update: Update, context: ContextTypes.DEFAULT_TYPE):
    logger.info(f"[Balance], chat_id: {update.effective_chat.id}, username: {update.effective_user.username}, user_id {update.effective_user.id}")
    with Session(engine) as session:
        user_service = UserService(session)
        user_id = update.effective_user.id
        user = user_service.get_user_by_id(user_id)

        pwd_decrypted_bytes = pwd_encryptor.decrypt(user.mt5_acc_pwd_enc)
        acc_balance = mt5service.get_balance(user.mt5_acc_number, pwd_decrypted_bytes.decode())

        balance_msg = (f"Валюта: {acc_balance.currency}\n"
                 f"Баланс: {acc_balance.balance}\n"
                 f"Капитал: {acc_balance.equity}\n"
                 f"Прибыль: {acc_balance.profit}\n"
                 # f"Маржа: {acc_balance.margin}\n\n"
                 f"на {datetime.utcnow().strftime('%Y-%m-%d %H:%M:%S UTC')}")

        await context.bot.send_message(chat_id=update.effective_chat.id,
                                       text=f"{BALANCE_PREFIX_MD2}\n" + "||" + helpers.escape_markdown(balance_msg, 2) + "||",
                                       parse_mode=ParseMode.MARKDOWN_V2)

        await send_main_menu(update, context)
        return ConversationStates.MAIN_MENU
