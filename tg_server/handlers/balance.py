import logging
from datetime import datetime

from sqlalchemy.orm.session import Session
from telegram import helpers, Update
from telegram.constants import ParseMode
from telegram.ext import ContextTypes

from tg_server.constants.Instruments import get_instrument_by_symbol, available_instruments
from tg_server.database.db import engine
from tg_server.handlers.commons import send_main_menu, remove_exponent, round_flt_to_4_decimal
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

        for inst in available_instruments:
            positions = mt5service.positions_get(user.mt5_acc_number, pwd_decrypted_bytes.decode(), inst.symbol)
            if positions is None or len(positions) == 0:
                await context.bot.send_message(chat_id=update.effective_chat.id, text="У вас нет открытых позиций.")
                await send_main_menu(update, context)
                return ConversationStates.MAIN_MENU
            elif len(positions) > 1:
                raise Exception("More than one position")

            sym_info = mt5service.symbol_info(user.mt5_acc_number, pwd_decrypted_bytes.decode(), positions[0].symbol)
            base_equivalent = round_flt_to_4_decimal(positions[0].volume) * sym_info.trade_contract_size
            instrument = get_instrument_by_symbol(positions[0].symbol)

            balance_msg = (
                     f"Продано: -{remove_exponent(base_equivalent)} {instrument.base}\n\n"
                     f"на {datetime.utcnow().strftime('%Y-%m-%d %H:%M:%S UTC')}")

            await context.bot.send_message(chat_id=update.effective_chat.id,
                                           text=f"{BALANCE_PREFIX_MD2}\n" + "||" + helpers.escape_markdown(balance_msg, 2) + "||",
                                           parse_mode=ParseMode.MARKDOWN_V2)

        await send_main_menu(update, context)
        return ConversationStates.MAIN_MENU
