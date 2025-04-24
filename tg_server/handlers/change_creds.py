import logging
from typing import List
from ptbcontrib.extract_passed_user import extract_passed_user

from sqlalchemy.orm.session import Session
from telegram import helpers, Update, ReplyKeyboardRemove, ReplyKeyboardMarkup, KeyboardButton, \
    KeyboardButtonRequestUsers
from telegram.constants import ParseMode
from telegram.ext import ContextTypes

from tg_server.database.db import engine
from tg_server.handlers.commons import send_main_menu, use_cancel_msg
from tg_server.handlers.states import ChangeCredsConvStates, ConversationStates
from tg_server.mt5.mt5service import mt5service
from tg_server.security.encryptor import pwd_encryptor
from tg_server.services.InviteLinkService import InviteLinkService
from tg_server.services.UserService import UserService

logger = logging.getLogger(__name__)

UD_KEY_CHANGE_CREDS_USER_ID = "UD_KEY_CHANGE_CREDS_USER_ID"

GENERATE_INVITE_PREFIX_MD2 = "*\[Изменение учетных данных пользователя\]*\n"

choose_user_markup = ReplyKeyboardMarkup(
    [[
        KeyboardButton("Выбрать пользователя",
                       request_users=KeyboardButtonRequestUsers(1, user_is_bot=False, request_username=True)),
    ]],
    one_time_keyboard=True, resize_keyboard=True)

async def change_creds_start(update: Update, context: ContextTypes.DEFAULT_TYPE):
    logger.info(f"[Change credentials|start], chat_id: {update.effective_chat.id}, username: {update.effective_user.username}, user_id {update.effective_user.id}")

    await update.message.reply_text(
        (GENERATE_INVITE_PREFIX_MD2
         + helpers.escape_markdown("Пожалуйста, выберете пользователя, используя меню"
                                   + use_cancel_msg
                                   , 2)) ,
        reply_markup=choose_user_markup,
        parse_mode=ParseMode.MARKDOWN_V2,
    )

    return ChangeCredsConvStates.PROMPT_USER


async def change_creds_shared_invalid(update: Update, context: ContextTypes.DEFAULT_TYPE):
    logger.info(f"[Change credentials|invalid_user], chat_id: {update.effective_chat.id}, username: {update.effective_user.username}, user_id {update.effective_user.id}, message: {update.message.text}")
    return await change_creds_start(update, context)


async def change_creds_shared(update: Update, context: ContextTypes.DEFAULT_TYPE):
    logger.info(f"[Change credentials|user_shared], chat_id: {update.effective_chat.id}, username: {update.effective_user.username}, user_id {update.effective_user.id}")
    shared_user = await extract_passed_user(message=update.message)

    with Session(engine) as session:
        user_service = UserService(session)
        contains_user = user_service.contains_user(shared_user.user_id)
        if not contains_user:
            logger.warning(f"[Change credentials|user_not_registered], chat_id: {update.effective_chat.id}, username: {update.effective_user.username}, user_id {update.effective_user.id}, message: {update.message.text}")

            await update.message.reply_text(
                GENERATE_INVITE_PREFIX_MD2 + helpers.escape_markdown("Этот пользователь не зарегистрирован. Выберите другого пользователя.", 2),
                reply_markup=choose_user_markup,
                parse_mode=ParseMode.MARKDOWN_V2,
                protect_content=True
            )
            return ChangeCredsConvStates.PROMPT_USER

    # Save the shared user in user_data
    context.user_data[UD_KEY_CHANGE_CREDS_USER_ID] = shared_user.user_id

    await update.message.reply_text(
        (GENERATE_INVITE_PREFIX_MD2
         + helpers.escape_markdown(
        "Пожалуйста, предоставьте данные учетной записи Gerchik в следующем формате:\n", 2)
             + "```формат \<account_number\> \<password\> ```"
             + "\n\n*__\(Сообщение с учетными данными будет удалено\)__*"),
        parse_mode=ParseMode.MARKDOWN_V2,
        reply_markup=ReplyKeyboardRemove(),
        protect_content=True
    )
    return ChangeCredsConvStates.PROMPT_CREDENTIALS

async def change_creds_invalid_account_credentials(update: Update, context: ContextTypes.DEFAULT_TYPE):
    logger.warning(f"[Change credentials|invalid_format_acc_creds], chat_id: {update.effective_chat.id}, username: {update.effective_user.username}, user_id {update.effective_user.id}, message: {update.message.text}")
    await update.message.reply_text(
        GENERATE_INVITE_PREFIX_MD2 + helpers.escape_markdown("Неверный формат учетных данных. Попробуйте еще раз.", 2),
        parse_mode=ParseMode.MARKDOWN_V2,
        protect_content=True
    )
    return ChangeCredsConvStates.PROMPT_CREDENTIALS

async def change_creds_account_credentials(update: Update, context: ContextTypes.DEFAULT_TYPE):
    logger.info(f"[Change credentials|acc_creds_input], chat_id: {update.effective_chat.id}, username: {update.effective_user.username}, user_id {update.effective_user.id}")

    credentials_arr: List[str] = update.message.text.split(' ')

    account_number_str = credentials_arr[0]
    account_pwd = credentials_arr[1]

    await update.message.delete()

    await update.message.reply_text(
        GENERATE_INVITE_PREFIX_MD2 + helpers.escape_markdown(f"Проверка учетных данных...", 2),
        parse_mode=ParseMode.MARKDOWN_V2,
        protect_content=True
    )

    account_number = int(account_number_str)
    with Session(engine) as session:
        invite_links_service = InviteLinkService(session)
        user_service = UserService(session)
        user_with_same_account = user_service.get_user_by_account_num(account_number)
        if user_with_same_account and user_with_same_account.tg_id != update.effective_user.id:
            logger.warning(f"[Change credentials|user_with_same_acc#], chat_id: {update.effective_chat.id}, username: {update.effective_user.username}, user_id {update.effective_user.id}")
            await update.message.reply_text(
                GENERATE_INVITE_PREFIX_MD2 + helpers.escape_markdown("Пользователь с таким номером счета уже существует. Введите учетные данные для другого счета.", 2),
                parse_mode=ParseMode.MARKDOWN_V2,
                protect_content=True
            )
            return ChangeCredsConvStates.PROMPT_CREDENTIALS

        link_by_account_num = invite_links_service.get_link_by_account_num(account_number)
        if link_by_account_num:
            logger.warning(f"[Change credentials|link_with_same_acc#], chat_id: {update.effective_chat.id}, username: {update.effective_user.username}, user_id {update.effective_user.id}")
            await update.message.reply_text(
                GENERATE_INVITE_PREFIX_MD2 + helpers.escape_markdown("Приглашение с таким же номером счета уже существует. Введите учетные данные для другого счета.", 2),
                parse_mode=ParseMode.MARKDOWN_V2,
                protect_content=True
            )
            return ChangeCredsConvStates.PROMPT_CREDENTIALS

        valid_creds = mt5service.check_credentials(account_number, account_pwd)
        if not valid_creds:
            logger.warning(f"[Change credentials|invalid_creds], chat_id: {update.effective_chat.id}, username: {update.effective_user.username}, user_id {update.effective_user.id}")
            await update.message.reply_text(
                GENERATE_INVITE_PREFIX_MD2 + helpers.escape_markdown("Учетные данные недействительны. Попробуйте еще раз.", 2),
                parse_mode=ParseMode.MARKDOWN_V2,
                protect_content=True
            )
            return ChangeCredsConvStates.PROMPT_CREDENTIALS

        shared_user_id: int = context.user_data.get(UD_KEY_CHANGE_CREDS_USER_ID)
        encrypted_pwd = pwd_encryptor.encrypt(account_pwd.encode())

        user_service.change_user_credentials(shared_user_id, account_number, encrypted_pwd)
        session.commit()

        await update.message.reply_text(
            GENERATE_INVITE_PREFIX_MD2 + helpers.escape_markdown(
                f"Учетные данные изменены успешно.", 2),
            parse_mode=ParseMode.MARKDOWN_V2,
            protect_content=True
        )

    await send_main_menu(update, context)
    return ConversationStates.MAIN_MENU

async def change_creds_cancel(update: Update, context: ContextTypes.DEFAULT_TYPE):
    logger.warning(f"[Change credentials|сфтсуд], chat_id: {update.effective_chat.id}, username: {update.effective_user.username}, user_id {update.effective_user.id}")
    user = update.message.from_user
    logger.info("User %s canceled the conversation.", user.first_name)
    await update.message.reply_text(GENERATE_INVITE_PREFIX_MD2 + helpers.escape_markdown("Отмена генерации приглашения.", 2),
                                    reply_markup=ReplyKeyboardRemove(), parse_mode=ParseMode.MARKDOWN_V2)
    await send_main_menu(update, context)
    return ConversationStates.MAIN_MENU


