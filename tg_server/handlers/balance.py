from decimal import Decimal
import logging
from datetime import datetime
from typing import Optional

from sqlalchemy.orm.session import Session
from telegram import helpers, Update, InlineKeyboardMarkup
from telegram.constants import ParseMode
from telegram.ext import ContextTypes

from tg_server.constants.Instruments import get_instrument_by_symbol, available_instruments
from tg_server.database.db import engine
from tg_server.database.models import TgUser
from tg_server.handlers.commons import main_menu_decorator
from tg_server.handlers.states import BalanceConvStates
from tg_server.mt5.mt5service import mt5service
from tg_server.security.encryptor import pwd_encryptor
from tg_server.services.UserService import UserService
from tg_server.utils.decimals import round_flt_to_4_decimal, remove_exponent, format_currency

logger = logging.getLogger(__name__)

BALANCE_PREFIX_MD2 = "*\[Баланс\]*\n"

def get_balance_per_instrument(user: TgUser):
    pwd_decrypted_bytes = pwd_encryptor.decrypt(user.mt5_acc_pwd_enc)
    result = {}
    for instrument in available_instruments:
        symbol_to_use = user.select_symbol(instrument)

        # TODO: (optimization) batch get position instead of one by one
        positions = mt5service.positions_get(user.mt5_acc_number, pwd_decrypted_bytes.decode(), symbol_to_use)
        if len(positions) == 1:
            sym_info = mt5service.symbol_info(user.mt5_acc_number, pwd_decrypted_bytes.decode(), positions[0].symbol)
            base_equivalent = round_flt_to_4_decimal(positions[0].volume) * sym_info.trade_contract_size
            result[instrument.symbol_standard] = base_equivalent
        else:
            logger.warning(f"[Balance][No open positions or >1][chat_id: {user.tg_id}][user_id: {user.tg_id}]")
            result[instrument.symbol_standard] = None
    return result

async def balance_command(update: Update, context: ContextTypes.DEFAULT_TYPE):
    logger.info(f"[Balance][start][chat_id: {update.effective_chat.id}][username: {update.effective_user.username}][user_id: {update.effective_user.id}]")

    query = update.callback_query
    await query.answer()

    with Session(engine) as session:
        user_service = UserService(session)
        user_id = update.effective_user.id
        user = user_service.get_user_by_id(user_id)

        if user.mt5_acc_number is None or user.mt5_acc_pwd_enc is None:
            logger.warning(f"[Balance][No related account][chat_id: {update.effective_chat.id}][username: {update.effective_user.username}][user_id: {update.effective_user.id}]")
            await query.edit_message_text(
                text=helpers.escape_markdown("У вас нет привязанной учетной записи для выполнения операции.", 2),
                reply_markup=InlineKeyboardMarkup(main_menu_decorator([])),
                parse_mode=ParseMode.MARKDOWN_V2
            )
            return BalanceConvStates.BALANCE_OPENED

        balance_msg = ""
        # Check if all values in inst_to_baseprice are None
        inst_to_baseprice = get_balance_per_instrument(user)
        if all(value is None for value in inst_to_baseprice.values()):
            balance_msg += "Нет открытых позиций по доступным инструментам.\n\n"
        else:
            for inst in inst_to_baseprice:
                instrument = get_instrument_by_symbol(inst)
                base_equivalent = inst_to_baseprice[inst]

                if base_equivalent is None:
                    continue

                amount_amnt_str = format_currency(remove_exponent(-base_equivalent))
                currency_str = instrument.currency_for_hedge()
                balance_msg += (
                        f"Захеджировано {instrument.label}:\n {amount_amnt_str} {currency_str}\n\n"
                        )

        await query.edit_message_text(
            text=f"{BALANCE_PREFIX_MD2}\n" + helpers.escape_markdown(balance_msg, 2),
            reply_markup=InlineKeyboardMarkup(main_menu_decorator([])),
            parse_mode=ParseMode.MARKDOWN_V2
        )

        return BalanceConvStates.BALANCE_OPENED
