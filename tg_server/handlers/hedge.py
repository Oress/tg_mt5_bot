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

async def hedge_operation_selected(update: Update, context: ContextTypes.DEFAULT_TYPE):
    type_str = update.message.text

    async def move():
        await update.message.reply_text(
            helpers.escape_markdown((
                "Instrument?"
                "\n\nUse /cancel command to go back to main menu."
            ), 2),
            reply_markup=select_instrument_menu_markup,
            parse_mode=ParseMode.MARKDOWN_V2,
        )

    if type_str in ["Buy", "Sell"]:
        context.user_data[UD_KEY_HEDGE_OPERATION] = type_str
        await move()
    else:
        await update.message.reply_text(
            helpers.escape_markdown((
                "Invalid type. Please select either 'Buy' or 'Sell'."
                "\n\nUse /cancel command to go back to main menu."
            ), 2),
            reply_markup=select_operation_menu_markup,
            parse_mode=ParseMode.MARKDOWN_V2,
        )
        return HedgeConvStates.SELECT_OPERATION

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

async def hedge_volume_selected(update: Update, context: ContextTypes.DEFAULT_TYPE):
    volume_str = update.message.text
    volume_dec = None

    try:
        volume_dec = Decimal(volume_str)
        context.user_data[UD_KEY_HEDGE_VOLUME] = volume_dec
    except (ValueError, TypeError):
        await update.message.reply_text(
            helpers.escape_markdown((
                "Invalid volume. Please enter a valid number."
                "\n\nUse /cancel command to go back to main menu."
            ), 2),
            reply_markup=ReplyKeyboardRemove(),
            parse_mode=ParseMode.MARKDOWN_V2,
        )
        return HedgeConvStates.SELECT_INSTRUMENT

    await update.message.reply_text(
        helpers.escape_markdown((
            "City?"
            "\n\nUse /cancel command to go back to main menu."
        ), 2),
        reply_markup=select_city_menu_markup,
        parse_mode=ParseMode.MARKDOWN_V2,
    )

    return HedgeConvStates.SELECT_CITY

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
    else:
        await update.message.reply_text(
            helpers.escape_markdown((
                "Please select either 'Place order' or 'Edit'."
                "\n\nUse /cancel command to go back to main menu."
            ), 2),
            reply_markup=preview_action_menu_markup,
            parse_mode=ParseMode.MARKDOWN_V2,
        )
        return HedgeConvStates.PREVIEW

