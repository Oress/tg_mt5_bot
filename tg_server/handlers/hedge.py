import logging
import os
from decimal import Decimal

from sqlalchemy.orm.session import Session
from telegram import helpers, Update, ReplyKeyboardMarkup, KeyboardButton, ReplyKeyboardRemove
from telegram.constants import ParseMode
from telegram.ext import ContextTypes

from tg_server.database.db import engine
from tg_server.handlers.commons import send_main_menu
from tg_server.handlers.states import ConversationStates, HedgeConvStates
from tg_server.mt5.mt5service import mt5service
from tg_server.security.encryptor import pwd_encryptor
from tg_server.services.UserService import UserService

logger = logging.getLogger(__name__)

HEDGE_PREFIX_MD2 = "*\[Hedge\]*\n"

# TODO: maybe add them to the DB tables instead.
inst_env_str = os.getenv("HEDGE_INSTRUMENTS")
inst_list = inst_env_str.split(";") if inst_env_str else []

cities_env_str = os.getenv("HEDGE_CITIES")
cities_list = cities_env_str.split(";") if cities_env_str else []
cities_list.sort()

UD_KEY_HEDGE_OPERATION = "UD_KEY_HEDGE_OPERATION"
UD_KEY_HEDGE_INSTRUMENT = "UD_KEY_HEDGE_INSTRUMENT"
UD_KEY_HEDGE_VOLUME = "UD_KEY_HEDGE_VOLUME"
UD_KEY_HEDGE_PRICE = "UD_KEY_HEDGE_PRICE"
UD_KEY_HEDGE_CITY = "UD_KEY_HEDGE_CITY"

select_operation_menu_markup = ReplyKeyboardMarkup(
    [[KeyboardButton("Buy"), KeyboardButton("Sell")]],
    one_time_keyboard=True,
    resize_keyboard=True
)

select_instrument_menu_markup = ReplyKeyboardMarkup(
    [inst_list[i:i+3] for i in range(0, len(inst_list), 3)],
    one_time_keyboard=True,
    resize_keyboard=True
)

select_city_menu_markup = ReplyKeyboardMarkup(
    [cities_list[i:i+3] for i in range(0, len(cities_list), 3)],
    one_time_keyboard=True,
    resize_keyboard=True
)

preview_action_menu_markup = ReplyKeyboardMarkup(
    [[KeyboardButton("Place order"), KeyboardButton("Edit")]],
    one_time_keyboard=True,
    resize_keyboard=True
)

async def hedge_cancel(update: Update, context: ContextTypes.DEFAULT_TYPE):
    await send_main_menu(update, context)
    return ConversationStates.MAIN_MENU

async def hedge_start(update: Update, context: ContextTypes.DEFAULT_TYPE):
    await update.message.reply_text(
        helpers.escape_markdown((
            "Type?"
            "\n\nUse /cancel command to go back to main menu."
        ), 2),
        reply_markup=select_operation_menu_markup,
        parse_mode=ParseMode.MARKDOWN_V2,
    )

    return HedgeConvStates.SELECT_OPERATION


async def hedge_invalid_operation_selected(update: Update, context: ContextTypes.DEFAULT_TYPE):
    await update.message.reply_text(
        helpers.escape_markdown((
            "Invalid type. Please select either 'Buy' or 'Sell'."
            "\n\nUse /cancel command to go back to main menu."
        ), 2),
        reply_markup=select_operation_menu_markup,
        parse_mode=ParseMode.MARKDOWN_V2,
    )
    return HedgeConvStates.SELECT_OPERATION


async def hedge_operation_selected(update: Update, context: ContextTypes.DEFAULT_TYPE):
    type_str = update.message.text
    context.user_data[UD_KEY_HEDGE_OPERATION] = type_str
    await update.message.reply_text(
        helpers.escape_markdown((
            "Instrument?"
            "\n\nUse /cancel command to go back to main menu."
        ), 2),
        reply_markup=select_instrument_menu_markup,
        parse_mode=ParseMode.MARKDOWN_V2,
    )

    return HedgeConvStates.SELECT_INSTRUMENT

async def hedge_invalid_instrument_selected(update: Update, context: ContextTypes.DEFAULT_TYPE):
    await update.message.reply_text(
        helpers.escape_markdown((
            "Invalid instrument. Please select a valid instrument."
            "\n\nUse /cancel command to go back to main menu."
        ), 2),
        parse_mode=ParseMode.MARKDOWN_V2,
    )
    return HedgeConvStates.SELECT_INSTRUMENT


async def hedge_instrument_selected(update: Update, context: ContextTypes.DEFAULT_TYPE):
    instrument_str = update.message.text

    if instrument_str not in inst_list:
        await update.message.reply_text(
            helpers.escape_markdown((
                "Invalid instrument. Please select a valid instrument."
                "\n\nUse /cancel command to go back to main menu."
            ), 2),
            parse_mode=ParseMode.MARKDOWN_V2,
        )
        return HedgeConvStates.SELECT_INSTRUMENT

    context.user_data[UD_KEY_HEDGE_INSTRUMENT] = instrument_str
    sym_info = None
    with Session(engine) as session:
        user_service = UserService(session)
        user_id = update.effective_user.id
        user = user_service.get_user_by_id(user_id)

        pwd_decrypted_bytes = pwd_encryptor.decrypt(user.mt5_acc_pwd_enc)
        sym_info = mt5service.symbol_info(user.mt5_acc_number, pwd_decrypted_bytes.decode(), instrument_str)

    symbol_info_msg = (f"Volume? \n\n"
                       f"Instrument: {sym_info.description}\n"
                       f"Contract size: {sym_info.trade_contract_size}\n"
                       f"Min volume: {sym_info.volume_min}\n"
                       f"Max volume: {sym_info.volume_max}\n"
                       f"Step: {sym_info.volume_step}\n\n"
                       "Use /cancel command to go back to main menu."
                       )
    await update.message.reply_text(
        helpers.escape_markdown((
            symbol_info_msg
        ), 2),
        reply_markup=ReplyKeyboardRemove(),
        parse_mode=ParseMode.MARKDOWN_V2,
    )
    # TODO: send message about lot size, min volume, max volume, step
    return HedgeConvStates.SELECT_VOLUME

async def hedge_invalid_volume_selected(update: Update, context: ContextTypes.DEFAULT_TYPE):
    await update.message.reply_text(
        helpers.escape_markdown((
            "Invalid volume. Please enter a valid number."
            "\n\nUse /cancel command to go back to main menu."
        ), 2),
        reply_markup=ReplyKeyboardRemove(),
        parse_mode=ParseMode.MARKDOWN_V2,
    )
    return HedgeConvStates.SELECT_INSTRUMENT


async def hedge_volume_selected(update: Update, context: ContextTypes.DEFAULT_TYPE):
    volume_str = update.message.text

    try:
        volume_flt = float(volume_str)
        context.user_data[UD_KEY_HEDGE_VOLUME] = volume_flt
    except (ValueError, TypeError):
       return hedge_invalid_volume_selected(update, context)

    await update.message.reply_text(
        helpers.escape_markdown((
            "City?"
            "\n\nUse /cancel command to go back to main menu."
        ), 2),
        reply_markup=select_city_menu_markup,
        parse_mode=ParseMode.MARKDOWN_V2,
    )

    return HedgeConvStates.SELECT_CITY

async def hedge_invalid_city_selected(update: Update, context: ContextTypes.DEFAULT_TYPE):
    city_str = update.message.text

    if city_str not in cities_list:
        await update.message.reply_text(
            helpers.escape_markdown((
                "Invalid city. Please select a valid city from the menu."
                "\n\nUse /cancel command to go back to main menu."
            ), 2),
            reply_markup=select_city_menu_markup,
            parse_mode=ParseMode.MARKDOWN_V2,
        )
        return HedgeConvStates.SELECT_CITY

    context.user_data[UD_KEY_HEDGE_CITY] = city_str

    preview_msg = (f"Preview: \n"
                   f"Operation: {context.user_data.get(UD_KEY_HEDGE_OPERATION)}\n"
                   f"Instrument: {context.user_data.get(UD_KEY_HEDGE_INSTRUMENT)}\n"
                   f"Volume: {context.user_data.get(UD_KEY_HEDGE_VOLUME)}\n"
                   f"Price: {context.user_data.get(UD_KEY_HEDGE_PRICE)}\n"
                   f"City: {context.user_data.get(UD_KEY_HEDGE_CITY)}\n"
                   )
    await update.message.reply_text(
        helpers.escape_markdown(preview_msg, 2),
        reply_markup=preview_action_menu_markup,
        parse_mode=ParseMode.MARKDOWN_V2,
    )
    return HedgeConvStates.PREVIEW


async def hedge_city_selected(update: Update, context: ContextTypes.DEFAULT_TYPE):
    city_str = update.message.text

    if city_str not in cities_list:
        await update.message.reply_text(
            helpers.escape_markdown((
                "Invalid city. Please select a valid city from the menu."
                "\n\nUse /cancel command to go back to main menu."
            ), 2),
            reply_markup=select_city_menu_markup,
            parse_mode=ParseMode.MARKDOWN_V2,
        )
        return HedgeConvStates.SELECT_CITY

    context.user_data[UD_KEY_HEDGE_CITY] = city_str

    preview_msg = (f"Preview: \n"
                   f"Operation: {context.user_data.get(UD_KEY_HEDGE_OPERATION)}\n"
                   f"Instrument: {context.user_data.get(UD_KEY_HEDGE_INSTRUMENT)}\n"
                   f"Volume: {context.user_data.get(UD_KEY_HEDGE_VOLUME)}\n"
                   f"Price: {context.user_data.get(UD_KEY_HEDGE_PRICE)}\n"
                   f"City: {context.user_data.get(UD_KEY_HEDGE_CITY)}\n"
                   )
    await update.message.reply_text(
        helpers.escape_markdown(preview_msg, 2),
        reply_markup=preview_action_menu_markup,
        parse_mode=ParseMode.MARKDOWN_V2,
    )
    return HedgeConvStates.PREVIEW

async def hedge_invalid_preview_choice(update: Update, context: ContextTypes.DEFAULT_TYPE):
    await update.message.reply_text(
        helpers.escape_markdown((
            "Please select either 'Place order' or 'Edit'."
            "\n\nUse /cancel command to go back to main menu."
        ), 2),
        reply_markup=preview_action_menu_markup,
        parse_mode=ParseMode.MARKDOWN_V2,
    )
    return HedgeConvStates.PREVIEW


async def hedge_preview_accepted(update: Update, context: ContextTypes.DEFAULT_TYPE):
    answer = update.message.text

    if answer == "Place order":
        # TODO: more info
        await update.message.reply_text(
            helpers.escape_markdown((
                "Placing order...\n"
            ), 2),
            parse_mode=ParseMode.MARKDOWN_V2,
        )

        with Session(engine) as session:
            user_service = UserService(session)
            user_id = update.effective_user.id
            user = user_service.get_user_by_id(user_id)

            pwd_decrypted_bytes = pwd_encryptor.decrypt(user.mt5_acc_pwd_enc)
            order_result = mt5service.place_order(user.mt5_acc_number,
                                                  pwd_decrypted_bytes.decode(),
                                                  context.user_data.get(UD_KEY_HEDGE_OPERATION) == "Buy",
                                                  context.user_data.get(UD_KEY_HEDGE_INSTRUMENT),
                                                  context.user_data.get(UD_KEY_HEDGE_VOLUME))

        await update.message.reply_text(
            HEDGE_PREFIX_MD2 + helpers.escape_markdown(report_on_order_send(order_result), 2),
            reply_markup=select_operation_menu_markup,
            parse_mode=ParseMode.MARKDOWN_V2,
        )


        await send_main_menu(update, context)
        return ConversationStates.MAIN_MENU
    elif answer == "Edit":
        await update.message.reply_text(
            helpers.escape_markdown((
                "Type?"
                "\n\nUse /cancel command to go back to main menu."
            ), 2),
            reply_markup=select_operation_menu_markup,
            parse_mode=ParseMode.MARKDOWN_V2,
        )
        return HedgeConvStates.SELECT_OPERATION

def report_on_order_send(order_result) -> str:
    """Returns a user-friendly message based on the MT5 return code."""
    messages = {
        10004: "Requote",
        10006: "Request rejected",
        10007: "Request canceled by trader",
        10008: "Order placed",
        10009: "Request completed",
        10010: "Only part of the request was completed",
        10011: "Request processing error",
        10012: "Request canceled by timeout",
        10013: "Invalid request",
        10014: "Invalid volume in the request",
        10015: "Invalid price in the request",
        10016: "Invalid stops in the request",
        10017: "Trade is disabled",
        10018: "Market is closed",
        10019: "There is not enough money to complete the request",
        10020: "Prices changed",
        10021: "There are no quotes to process the request",
        10022: "Invalid order expiration date in the request",
        10023: "Order state changed",
        10024: "Too frequent requests",
        10025: "No changes in request",
        10026: "Autotrading disabled by server",
        10027: "Autotrading disabled by client terminal",
        10028: "Request locked for processing",
        10029: "Order or position frozen",
        10030: "Invalid order filling type",
        10031: "No connection with the trade server",
        10032: "Operation is allowed only for live accounts",
        10033: "The number of pending orders has reached the limit",
        10034: "The volume of orders and positions for the symbol has reached the limit",
        10035: "Incorrect or prohibited order type",
        10036: "Position with the specified POSITION_IDENTIFIER has already been closed",
        10038: "A close volume exceeds the current position volume",
        10039: "A close order already exists for a specified position. This may happen when working in the hedging system:",
    }

    if order_result.retcode in [10008, 10009, 10010]:
        return f"Order placed successfully."
    elif order_result.retcode in messages:
        return f"Order failed. Reason: {messages[order_result.retcode]}"
    else:
        return f"Order failed. Unknown error code {order_result.retcode}."