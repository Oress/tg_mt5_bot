import logging
import os
from datetime import datetime
from decimal import Decimal, ROUND_HALF_UP

from sqlalchemy.orm.session import Session
from telegram import helpers, Update, ReplyKeyboardMarkup, KeyboardButton, ReplyKeyboardRemove
from telegram.constants import ParseMode
from telegram.ext import ContextTypes

from tg_server.constants.Instruments import available_instruments, get_instrument_by_label, get_instrument_by_symbol
from tg_server.database.db import engine
from tg_server.database.models import Order
from tg_server.handlers.commons import send_main_menu, KeyboardButtons, remove_exponent, round_flt_to_4_decimal
from tg_server.handlers.states import ConversationStates, HedgeConvStates
from tg_server.mt5.mt5service import mt5service
from tg_server.security.encryptor import pwd_encryptor
from tg_server.services.OrderService import OrderService
from tg_server.services.UserService import UserService

logger = logging.getLogger(__name__)

HEDGE_PREFIX_MD2 = "*\[Хедж\]*\n"

# TODO: maybe add them to the DB tables instead.
inst_list = [x.label for x in available_instruments]

cities_env_str = os.getenv("HEDGE_CITIES")
cities_list = cities_env_str.split(";") if cities_env_str else []
cities_list.sort()

UD_KEY_HEDGE_OPERATION = "UD_KEY_HEDGE_OPERATION"
UD_KEY_HEDGE_INSTRUMENT = "UD_KEY_HEDGE_INSTRUMENT"
UD_KEY_HEDGE_VOLUME = "UD_KEY_HEDGE_VOLUME"
UD_KEY_HEDGE_EURO = "UD_KEY_HEDGE_EURO"
UD_KEY_HEDGE_PRICE = "UD_KEY_HEDGE_PRICE"
UD_KEY_HEDGE_CITY = "UD_KEY_HEDGE_CITY"

select_operation_menu_markup = ReplyKeyboardMarkup(
    [[KeyboardButton(KeyboardButtons.BUY), KeyboardButton(KeyboardButtons.SELL)]],
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
    [[KeyboardButton(KeyboardButtons.PLACE_ORDER), KeyboardButton(KeyboardButtons.PREVIEW_EDIT)]],
    one_time_keyboard=True,
    resize_keyboard=True
)

use_cancel_msg = "\n\nИспользуйте /cancel, чтобы вернуться в главное меню."

async def hedge_cancel(update: Update, context: ContextTypes.DEFAULT_TYPE):
    logger.info(f"[Cancel Hedge], chat_id: {update.effective_chat.id}, username: {update.effective_user.username}, user_id {update.effective_user.id}")
    await send_main_menu(update, context)
    return ConversationStates.MAIN_MENU

async def hedge_start(update: Update, context: ContextTypes.DEFAULT_TYPE):
    logger.info(f"[Hedge Start], chat_id: {update.effective_chat.id}, username: {update.effective_user.username}, user_id {update.effective_user.id}")
    await update.message.reply_text(
        HEDGE_PREFIX_MD2 + helpers.escape_markdown((
            "Операция?"
            + use_cancel_msg
        ), 2),
        reply_markup=select_operation_menu_markup,
        parse_mode=ParseMode.MARKDOWN_V2,
    )

    return HedgeConvStates.SELECT_OPERATION


async def hedge_invalid_operation_selected(update: Update, context: ContextTypes.DEFAULT_TYPE):
    logger.warning(f"[Hedge Invalid Operation], chat_id: {update.effective_chat.id}, username: {update.effective_user.username}, user_id {update.effective_user.id}, message: {update.effective_message.text} ")
    await update.message.reply_text(
        HEDGE_PREFIX_MD2 + helpers.escape_markdown((
            f"Неверный тип операции. Выберите «{KeyboardButtons.BUY}» или «{KeyboardButtons.SELL}»."
            + use_cancel_msg
        ), 2),
        reply_markup=select_operation_menu_markup,
        parse_mode=ParseMode.MARKDOWN_V2,
    )
    return HedgeConvStates.SELECT_OPERATION


async def hedge_operation_selected(update: Update, context: ContextTypes.DEFAULT_TYPE):
    logger.info(f"[Hedge Operation Selected], chat_id: {update.effective_chat.id}, username: {update.effective_user.username}, user_id {update.effective_user.id}, message: {update.effective_message.text}")
    type_str = update.message.text
    context.user_data[UD_KEY_HEDGE_OPERATION] = type_str
    await update.message.reply_text(
        HEDGE_PREFIX_MD2 + helpers.escape_markdown((
            "Инструмент?"
            + use_cancel_msg
        ), 2),
        reply_markup=select_instrument_menu_markup,
        parse_mode=ParseMode.MARKDOWN_V2,
    )

    return HedgeConvStates.SELECT_INSTRUMENT

async def hedge_invalid_instrument_selected(update: Update, context: ContextTypes.DEFAULT_TYPE):
    logger.warning(f"[Hedge Invalid Instrument], chat_id: {update.effective_chat.id}, username: {update.effective_user.username}, user_id {update.effective_user.id}, message: {update.effective_message.text}")
    await update.message.reply_text(
        HEDGE_PREFIX_MD2 + helpers.escape_markdown((
            "Недействительный инструмент. Выберите действительный инструмент из меню."
            + use_cancel_msg
        ), 2),
        reply_markup=select_instrument_menu_markup,
        parse_mode=ParseMode.MARKDOWN_V2,
    )
    return HedgeConvStates.SELECT_INSTRUMENT


async def hedge_instrument_selected(update: Update, context: ContextTypes.DEFAULT_TYPE):
    logger.info(f"[Hedge Instrument Selected], chat_id: {update.effective_chat.id}, username: {update.effective_user.username}, user_id {update.effective_user.id}, message: {update.effective_message.text}")
    instrument = get_instrument_by_label(update.message.text)
    instrument_str = instrument.symbol

    sym_info = None
    with Session(engine) as session:
        user_service = UserService(session)
        user_id = update.effective_user.id
        user = user_service.get_user_by_id(user_id)

        pwd_decrypted_bytes = pwd_encryptor.decrypt(user.mt5_acc_pwd_enc)

        operation = context.user_data[UD_KEY_HEDGE_OPERATION]
        sym_info = mt5service.symbol_info(user.mt5_acc_number, pwd_decrypted_bytes.decode(), instrument_str)

        if operation == KeyboardButtons.BUY:
            avail_to_spend_dcm = Decimal(0)
            open_positions = mt5service.positions_get(user.mt5_acc_number, pwd_decrypted_bytes.decode(), instrument_str)
            if len(open_positions) == 0:
                await update.message.reply_text(
                    HEDGE_PREFIX_MD2 + helpers.escape_markdown("Нет активных ордеров, пожалуйста начните сначала.", 2),
                    reply_markup=ReplyKeyboardRemove(),
                    parse_mode=ParseMode.MARKDOWN_V2,
                    )
                return await hedge_start(update, context)
            else:
                print(open_positions)
                avail_to_spend_dcm = round_flt_to_4_decimal(open_positions[0].volume) * Decimal(sym_info.trade_contract_size)

            symbol_info_msg = (f"Сколько ({instrument.base})?\n"
                               f"Инструмент: {sym_info.description}\n"
                               f"Доступно для покупки: {remove_exponent(avail_to_spend_dcm)} {instrument.base}"
                               # f"Мин. объем: {sym_info.volume_min}\n"
                               # f"Макс. объем: {sym_info.volume_max}\n"
                               # f"Шаг: {sym_info.volume_step}"
                               + use_cancel_msg
                               )
        elif operation == KeyboardButtons.SELL:
            symbol_info_msg = (f"Сколько ({instrument.base})?\n"
                               f"Инструмент: {sym_info.description}"
                               # f"Мин. объем: {sym_info.volume_min}\n"
                               # f"Макс. объем: {sym_info.volume_max}\n"
                               # f"Шаг: {sym_info.volume_step}"
                               + use_cancel_msg
                               )
        context.user_data[UD_KEY_HEDGE_INSTRUMENT] = instrument_str

        # To enable symbol in the MT5 terminal and get the price in the next step, otherwise it will return 0
        mt5service.get_price_for_short(
            user.mt5_acc_number,
            pwd_decrypted_bytes.decode(),
            context.user_data.get(UD_KEY_HEDGE_INSTRUMENT)
        )


    await update.message.reply_text(
        HEDGE_PREFIX_MD2 + helpers.escape_markdown(symbol_info_msg, 2),
        reply_markup=ReplyKeyboardRemove(),
        parse_mode=ParseMode.MARKDOWN_V2,
    )
    return HedgeConvStates.SELECT_VOLUME

async def hedge_invalid_volume_selected(update: Update, context: ContextTypes.DEFAULT_TYPE):
    logger.warning(f"[Hedge Invalid Volume], chat_id: {update.effective_chat.id}, username: {update.effective_user.username}, user_id {update.effective_user.id}, message: {update.effective_message.text}")
    await update.message.reply_text(
        HEDGE_PREFIX_MD2 + helpers.escape_markdown((
            "Неверная сумма. Введите допустимое число."
            + use_cancel_msg
        ), 2),
        reply_markup=ReplyKeyboardRemove(),
        parse_mode=ParseMode.MARKDOWN_V2,
    )
    return HedgeConvStates.SELECT_VOLUME


async def hedge_volume_selected(update: Update, context: ContextTypes.DEFAULT_TYPE):
    logger.info(f"[Hedge Volume Selected], chat_id: {update.effective_chat.id}, username: {update.effective_user.username}, user_id {update.effective_user.id} message: {update.effective_message.text}")
    eur_str = update.message.text

    try:
        eur_dcm = Decimal(eur_str)
        sym_info = None
        with Session(engine) as session:
            user_service = UserService(session)
            user_id = update.effective_user.id
            user = user_service.get_user_by_id(user_id)

            pwd_decrypted_bytes = pwd_encryptor.decrypt(user.mt5_acc_pwd_enc)

            instrument_str = context.user_data.get(UD_KEY_HEDGE_INSTRUMENT)
            sym_info = mt5service.symbol_info(user.mt5_acc_number, pwd_decrypted_bytes.decode(), instrument_str)

            operation = context.user_data[UD_KEY_HEDGE_OPERATION]

            if operation == KeyboardButtons.BUY:
                open_positions = mt5service.positions_get(user.mt5_acc_number, pwd_decrypted_bytes.decode(), instrument_str)
                avail_to_spend = round_flt_to_4_decimal(open_positions[0].volume) * Decimal(sym_info.trade_contract_size)

        # Check the euro amnt is more than the min amount
        min_amount_eur = sym_info.volume_min * sym_info.trade_contract_size
        if eur_dcm < min_amount_eur:
            await update.message.reply_text(
                HEDGE_PREFIX_MD2 + helpers.escape_markdown(f"Минимальная сумма: {remove_exponent(min_amount_eur.normalize())}", 2),
                reply_markup=ReplyKeyboardRemove(),
                parse_mode=ParseMode.MARKDOWN_V2,
                )
            return await hedge_invalid_volume_selected(update, context)

        # Check the euro amnt is less than the available amount
        max_amount_eur = sym_info.volume_max * sym_info.trade_contract_size
        if eur_dcm > max_amount_eur:
            await update.message.reply_text(
                HEDGE_PREFIX_MD2 + helpers.escape_markdown(f"Максимальная сумма: {remove_exponent(max_amount_eur.normalize())}", 2),
                reply_markup=ReplyKeyboardRemove(),
                parse_mode=ParseMode.MARKDOWN_V2,
                )
            return await hedge_invalid_volume_selected(update, context)

        # Check the euro amnt is divisible by the step
        step_eur = sym_info.volume_step * sym_info.trade_contract_size
        if eur_dcm % step_eur != 0:
            await update.message.reply_text(
                HEDGE_PREFIX_MD2 + helpers.escape_markdown(f"Сумма должна быть кратна: {remove_exponent(step_eur.normalize())}", 2),
                reply_markup=ReplyKeyboardRemove(),
                parse_mode=ParseMode.MARKDOWN_V2,
                )
            return await hedge_invalid_volume_selected(update, context)

        # In case of buying, check the euro amnt is less than the available amount
        if operation == KeyboardButtons.BUY and avail_to_spend:
            if eur_dcm > avail_to_spend:
                await update.message.reply_text(
                    HEDGE_PREFIX_MD2 + helpers.escape_markdown(f"Максимальная сумма: {remove_exponent(avail_to_spend.normalize())}", 2),
                    reply_markup=ReplyKeyboardRemove(),
                    parse_mode=ParseMode.MARKDOWN_V2,
                    )
                return await hedge_invalid_volume_selected(update, context)

        context.user_data[UD_KEY_HEDGE_VOLUME] = str(eur_dcm / sym_info.trade_contract_size)
        context.user_data[UD_KEY_HEDGE_EURO] = str(eur_dcm)
    except (ValueError, TypeError):
       return hedge_invalid_volume_selected(update, context)

    await update.message.reply_text(
        HEDGE_PREFIX_MD2 + helpers.escape_markdown((
            "Город?"
            + use_cancel_msg
        ), 2),
        reply_markup=select_city_menu_markup,
        parse_mode=ParseMode.MARKDOWN_V2,
    )

    return HedgeConvStates.SELECT_CITY


async def hedge_invalid_city_selected(update: Update, context: ContextTypes.DEFAULT_TYPE):
    logger.warning(f"[Hedge Invalid City], chat_id: {update.effective_chat.id}, username: {update.effective_user.username}, user_id {update.effective_user.id}, message: {update.effective_message.text}")
    await update.message.reply_text(
        HEDGE_PREFIX_MD2 + helpers.escape_markdown((
            "Неверный город. Выберите допустимый город из меню."
            + use_cancel_msg
        ), 2),
        reply_markup=select_city_menu_markup,
        parse_mode=ParseMode.MARKDOWN_V2,
    )
    return HedgeConvStates.SELECT_CITY


async def hedge_city_selected(update: Update, context: ContextTypes.DEFAULT_TYPE):
    logger.info(f"[Hedge City Selected], chat_id: {update.effective_chat.id}, username: {update.effective_user.username}, user_id {update.effective_user.id}, message: {update.effective_message.text}")
    city_str = update.message.text

    if city_str not in cities_list:
        await update.message.reply_text(
            HEDGE_PREFIX_MD2 + helpers.escape_markdown((
                "Неверный город. Выберите допустимый город из меню."
                + use_cancel_msg
            ), 2),
            reply_markup=select_city_menu_markup,
            parse_mode=ParseMode.MARKDOWN_V2,
        )
        return HedgeConvStates.SELECT_CITY

    context.user_data[UD_KEY_HEDGE_CITY] = city_str

    with Session(engine) as session:
        user_service = UserService(session)
        user_id = update.effective_user.id
        user = user_service.get_user_by_id(user_id)
        pwd_decrypted_bytes = pwd_encryptor.decrypt(user.mt5_acc_pwd_enc)

        price_per_unit = mt5service.get_price_for_short(
            user.mt5_acc_number,
            pwd_decrypted_bytes.decode(),
            context.user_data.get(UD_KEY_HEDGE_INSTRUMENT),
        )


    instrument = get_instrument_by_symbol(context.user_data.get(UD_KEY_HEDGE_INSTRUMENT))
    now_dt_utc = datetime.utcnow().strftime('%Y-%m-%d %H:%M:%S UTC')
    preview_msg = (f"Предварительный просмотр: \n"
                   f"Операция: {context.user_data.get(UD_KEY_HEDGE_OPERATION)}\n"
                   f"Инструмент: {instrument.label}\n"
                   f"Сколько: {context.user_data.get(UD_KEY_HEDGE_EURO)} {instrument.base}\n"
                   f"Цена (на {now_dt_utc}): {price_per_unit}\n"
                   f"Город: {context.user_data.get(UD_KEY_HEDGE_CITY)}\n"
                   )
    await update.message.reply_text(
        HEDGE_PREFIX_MD2 + helpers.escape_markdown(preview_msg, 2),
        reply_markup=preview_action_menu_markup,
        parse_mode=ParseMode.MARKDOWN_V2,
    )
    return HedgeConvStates.PREVIEW

async def hedge_invalid_preview_choice(update: Update, context: ContextTypes.DEFAULT_TYPE):
    logger.warning(f"[Hedge Invalid Preview], chat_id: {update.effective_chat.id}, username: {update.effective_user.username}, user_id {update.effective_user.id}, message: {update.effective_message.text}")
    await update.message.reply_text(
        HEDGE_PREFIX_MD2 + helpers.escape_markdown((
            "Неверный выбор. Выберите «Размесить ордер» или «Изменить»."
            + use_cancel_msg
        ), 2),
        reply_markup=preview_action_menu_markup,
        parse_mode=ParseMode.MARKDOWN_V2,
    )
    return HedgeConvStates.PREVIEW


async def hedge_preview_accepted(update: Update, context: ContextTypes.DEFAULT_TYPE):
    logger.info(f"[Hedge Place Order], chat_id: {update.effective_chat.id}, username: {update.effective_user.username}, user_id {update.effective_user.id}, message: {update.effective_message.text}")
    answer = update.message.text

    if answer == KeyboardButtons.PLACE_ORDER:
        # TODO: more info
        await update.message.reply_text(
            HEDGE_PREFIX_MD2 + helpers.escape_markdown("Размещаю ордер...\n", 2),
            parse_mode=ParseMode.MARKDOWN_V2,
        )

        with Session(engine) as session:
            order_service = OrderService(session)
            user_service = UserService(session)
            user_id = update.effective_user.id
            user = user_service.get_user_by_id(user_id)

            pwd_decrypted_bytes = pwd_encryptor.decrypt(user.mt5_acc_pwd_enc)

            symbol = context.user_data.get(UD_KEY_HEDGE_INSTRUMENT)

            operation = context.user_data[UD_KEY_HEDGE_OPERATION]
            if operation == KeyboardButtons.BUY:
                positions = mt5service.positions_get(user.mt5_acc_number, pwd_decrypted_bytes.decode(), symbol)
                if len(positions) != 0:
                    order_result = mt5service.close_short_order(user.mt5_acc_number,
                                                                pwd_decrypted_bytes.decode(),
                                                                positions[0].ticket,
                                                                context.user_data.get(UD_KEY_HEDGE_INSTRUMENT),
                                                                Decimal(context.user_data.get(UD_KEY_HEDGE_VOLUME)))
                    if order_result is None:
                        raise RuntimeError(f"Something went wrong")
                    elif order_result.retcode not in [10008, 10009, 10010]:
                        logger.error(f"[Hedge Place Order Error] No recent order found for user_id: {user_id}")
                        await update.message.reply_text(
                            HEDGE_PREFIX_MD2 + helpers.escape_markdown(report_on_order_send(order_result), 2),
                            parse_mode=ParseMode.MARKDOWN_V2)
                        await send_main_menu(update, context)
                        return ConversationStates.MAIN_MENU

                    new_order: Order = order_service.save_close_order_to_db(user_id,
                                                                            context.user_data.get(UD_KEY_HEDGE_CITY),
                                                                            user.mt5_acc_number,
                                                                            context.user_data.get(UD_KEY_HEDGE_EURO),
                                                                            order_result)
                    session.commit()
                    order_msg = (
                        f"Ордер #: {new_order.id}\n"
                        f"Цена: {new_order.price}\n"
                        f"Город: {new_order.city}\n"
                        f"Дата: {new_order.created_at.strftime('%Y-%m-%d %H:%M:%S UTC')}\n"
                    )
                else:
                    logger.error(f"[Hedge Place Order Error] No recent order found for user_id: {user_id}")
                    raise RuntimeError(f"Incorrect trade account credentials")
            elif operation == KeyboardButtons.SELL:
                order_result = mt5service.place_short_order(user.mt5_acc_number,
                                                            pwd_decrypted_bytes.decode(),
                                                            context.user_data.get(UD_KEY_HEDGE_INSTRUMENT),
                                                            Decimal(context.user_data.get(UD_KEY_HEDGE_VOLUME)))
                new_order: Order = order_service.save_short_order_to_db(user_id,
                                                                        context.user_data.get(UD_KEY_HEDGE_CITY),
                                                                        user.mt5_acc_number,
                                                                        context.user_data.get(UD_KEY_HEDGE_EURO),
                                                                        order_result)
                session.commit()
                order_msg = (
                    f"Ордер #: {new_order.id}\n"
                    f"Цена: {new_order.price}\n"
                    f"Город: {new_order.city}\n"
                    f"Дата: {new_order.created_at.strftime('%Y-%m-%d %H:%M:%S UTC')}\n"
                            )
            else:
                await update.message.reply_text(
                    HEDGE_PREFIX_MD2 + helpers.escape_markdown((
                        "Неверный тип операции."
                        + use_cancel_msg
                    ), 2),
                    reply_markup=select_operation_menu_markup,
                    parse_mode=ParseMode.MARKDOWN_V2,
                )
                return HedgeConvStates.SELECT_OPERATION

        await update.message.reply_text(
            HEDGE_PREFIX_MD2 + helpers.escape_markdown(report_on_order_send(order_result), 2)
            + "\n\n"
            + helpers.escape_markdown(order_msg, 2),
            reply_markup=select_operation_menu_markup,
            parse_mode=ParseMode.MARKDOWN_V2,
        )

        await send_main_menu(update, context)
        return ConversationStates.MAIN_MENU
    elif answer == KeyboardButtons.PREVIEW_EDIT:
        await update.message.reply_text(
            helpers.escape_markdown((
                "Тип?"
                + use_cancel_msg
            ), 2),
            reply_markup=select_operation_menu_markup,
            parse_mode=ParseMode.MARKDOWN_V2,
        )
        return HedgeConvStates.SELECT_OPERATION

def report_on_order_send(order_result) -> str:
    """Returns a user-friendly message based on the MT5 return code."""
    messages = {
        10004: "Requote",
        10006: "Запрос отклонен",
        10007: "Запрос отменен трейдером",
        10008: "Ордер размещен",
        10009: "Запрос выполнен",
        10010: "Была выполнена только часть запроса",
        10011: "Ошибка обработки запроса",
        10012: "Запрос отменен из-за тайм-аута",
        10013: "Неверный запрос",
        10014: "Неверный объем в запросе",
        10015: "Неверная цена в запросе",
        10016: "Неверные остановки в запросе",
        10017: "Торговля отключена",
        10018: "Рынок закрыт.",
        10019: "Недостаточно денег для выполнения запроса",
        10020: "Цены изменены",
        10021: "Нет котировок для обработки запроса",
        10022: "Неверная дата окончания срока действия ордера в запросе",
        10023: "Состояние ордера изменено",
        10024: "Слишком частые запросы",
        10025: "Никаких изменений в запросе.",
        10026: "Автоторговля отключена сервером",
        10027: "Автоторговля отключена клиентским терминалом",
        10028: "Запрос заблокирован для обработки",
        10029: "Ордер или позиция заморожены",
        10030: "Неверный тип заполнения ордера",
        10031: "Нет соединения с торговым сервером",
        10032: "Операция разрешена только для реальных счетов.",
        10033: "Количество ожидающих ордеров достигло предела",
        10034: "Объем ордеров и позиций по символу достиг предела",
        10035: "Неправильный или запрещенный тип ордера",
        10036: "Позиция с указанным POSITION_IDENTIFIER уже закрыта",
        10038: "Объем закрытия превышает объем текущей позиции",
        10039: "Для указанной позиции уже существует ордер на закрытие.",
    }

    if order_result.retcode in [10008, 10009, 10010]:
        logger.info(f"[Hedge Place Order Success] {order_result}")
        return f"Ордер успешно размещен"
    elif order_result.retcode in messages:
        logger.error(f"[Hedge Place Order Error] {order_result.retcode}, {order_result}")
        return f"Ошибка. Причина: {messages[order_result.retcode]}"
    else:
        logger.error(f"[Hedge Place Order Error] {order_result.retcode}, {order_result}")
        return f"Ошибка. Неизвестный код ошибки {order_result.retcode}."