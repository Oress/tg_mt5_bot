import logging
from typing import List

from ptbcontrib.extract_passed_user import extract_passed_user
from sqlalchemy.orm.session import Session
from telegram import helpers, Update, ReplyKeyboardMarkup, KeyboardButton, \
    KeyboardButtonRequestUsers, InlineKeyboardMarkup
from telegram.constants import ParseMode
from telegram.ext import ContextTypes

from tg_server.database.db import engine
from tg_server.handlers.commons import main_menu_decorator, add_message_to_delete, purge_messages, \
    UD_KEY_MENU_MSG_ID, ACCOUNT_CREDS_FORMAT
from tg_server.handlers.states import UnblockUserConvStates
from tg_server.mt5.mt5service import mt5service
from tg_server.security.encryptor import pwd_encryptor
from tg_server.services.InviteLinkService import InviteLinkService
from tg_server.services.UserService import UserService

logger = logging.getLogger(__name__)

UD_KEY_USER_ID_UNBLOCK = "UD_KEY_USER_ID_UNBLOCK"

choose_user_markup = ReplyKeyboardMarkup(
    [[
        KeyboardButton("Выбрать пользователя",
                       request_users=KeyboardButtonRequestUsers(1, user_is_bot=False, request_username=True)),
    ]],
    one_time_keyboard=True, resize_keyboard=True)

async def unblock_user_start(update: Update, context: ContextTypes.DEFAULT_TYPE):
    logger.info(f"[Unblock user] start, chat_id: {update.effective_chat.id}, username: {update.effective_user.username}, user_id {update.effective_user.id}")

    query = update.callback_query
    await query.answer()

    await query.edit_message_text(
        text=helpers.escape_markdown("Пожалуйста, выберете пользователя, используя меню", 2),
        reply_markup=InlineKeyboardMarkup(main_menu_decorator([])),
        parse_mode=ParseMode.MARKDOWN_V2
    )

    prompt_msg = await update.effective_user.send_message(
        text="Выберете пользователя:",
        reply_markup=choose_user_markup,
        parse_mode=ParseMode.MARKDOWN_V2,
    )
    add_message_to_delete([prompt_msg], context);

    return UnblockUserConvStates.PROMPT_USER


# async def unblock_users_shared_invalid(update: Update, context: ContextTypes.DEFAULT_TYPE):
#     logger.info(f"[Unblock user] invalid user, chat_id: {update.effective_chat.id}, username: {update.effective_user.username}, user_id {update.effective_user.id}")
#     return await unblock_user_start(update, context)


async def unblock_users_shared(update: Update, context: ContextTypes.DEFAULT_TYPE):
    logger.info(f"[Unblock user] user shared, chat_id: {update.effective_chat.id}, username: {update.effective_user.username}, user_id {update.effective_user.id}")
    shared_user = await extract_passed_user(message=update.message)

    add_message_to_delete([update.message], context)
    await purge_messages(update, context)
    view_msg_id = context.user_data.get(UD_KEY_MENU_MSG_ID)

    with Session(engine) as session:
        user_service = UserService(session)
        user = user_service.get_user_by_id(shared_user.user_id)
        if not user:
            logger.warning(f"[Unblock user] user not registered, chat_id: {update.effective_chat.id}, username: {update.effective_user.username}, user_id {update.effective_user.id}")
            await context.bot.edit_message_text(
                chat_id=update.effective_chat.id,
                message_id=view_msg_id,
                text=helpers.escape_markdown("Этот пользователь не зарегистрирован. Выберите другого пользователя.", 2),
                reply_markup=InlineKeyboardMarkup(main_menu_decorator([])),
                parse_mode=ParseMode.MARKDOWN_V2
            )

            prompt_msg = await update.effective_user.send_message(
                text="Выберете пользователя:",
                reply_markup=choose_user_markup,
                parse_mode=ParseMode.MARKDOWN_V2,
            )
            add_message_to_delete([prompt_msg], context)
            return UnblockUserConvStates.PROMPT_USER

        elif not user.disabled:
            logger.warning(f"[Unblock user][user not blocked], chat_id: {update.effective_chat.id}, username: {update.effective_user.username}, user_id {update.effective_user.id}")
            await context.bot.edit_message_text(
                chat_id=update.effective_chat.id,
                message_id=view_msg_id,
                text=helpers.escape_markdown("Этот пользователь не заблокирован. Выберите другого пользователя.", 2),
                reply_markup=InlineKeyboardMarkup(main_menu_decorator([])),
                parse_mode=ParseMode.MARKDOWN_V2
            )

            prompt_msg = await update.effective_user.send_message(
                text="Выберете пользователя:",
                reply_markup=choose_user_markup,
                parse_mode=ParseMode.MARKDOWN_V2,
            )
            add_message_to_delete([prompt_msg], context)
            return UnblockUserConvStates.PROMPT_USER

    # Save the shared user in user_data
    context.user_data[UD_KEY_USER_ID_UNBLOCK] = shared_user.user_id


    await context.bot.edit_message_text(
        chat_id=update.effective_chat.id,
        message_id=view_msg_id,
        text=helpers.escape_markdown(
            "Пожалуйста, предоставьте данные учетной записи Gerchik в следующем формате:\n", 2)
             + "```формат \<account_number\> \<password\> ```",
        reply_markup=InlineKeyboardMarkup(main_menu_decorator([])),
        parse_mode=ParseMode.MARKDOWN_V2
    )
    return UnblockUserConvStates.PROMPT_CREDENTIALS

# async def unblock_invalid_account_credentials(update: Update, context: ContextTypes.DEFAULT_TYPE):
#     logger.warning(f"[Unblock user] invalid creds, chat_id: {update.effective_chat.id}, username: {update.effective_user.username}, user_id {update.effective_user.id}")
#     await update.message.reply_text(
#         helpers.escape_markdown("Неверный формат учетных данных. Попробуйте еще раз.", 2),
#         parse_mode=ParseMode.MARKDOWN_V2,
#         protect_content=True
#     )
#     return UnblockUserConvStates.PROMPT_CREDENTIALS

async def unblock_account_credentials(update: Update, context: ContextTypes.DEFAULT_TYPE):
    logger.info(f"[Unblock user] creds entered, chat_id: {update.effective_chat.id}, username: {update.effective_user.username}, user_id {update.effective_user.id}")

    credentials_arr: List[str] = update.message.text.split(' ')

    account_number_str = credentials_arr[0]
    account_pwd = credentials_arr[1]

    await update.message.delete()

    view_msg_id = context.user_data.get(UD_KEY_MENU_MSG_ID)

    await context.bot.edit_message_text(
        chat_id=update.effective_chat.id,
        message_id=view_msg_id,
        text=helpers.escape_markdown(f"Проверка учетных данных...", 2),
        parse_mode=ParseMode.MARKDOWN_V2
    )

    account_number = int(account_number_str)
    with Session(engine) as session:
        user_service = UserService(session)
        user_with_same_account = user_service.get_user_by_account_num(account_number)
        if user_with_same_account:
            logger.warning(f"[Unblock user] user with same account#, chat_id: {update.effective_chat.id}, username: {update.effective_user.username}, user_id {update.effective_user.id}")

            await context.bot.edit_message_text(
                chat_id=update.effective_chat.id,
                message_id=view_msg_id,
                text=helpers.escape_markdown("Пользователь с таким номером счета уже существует. Введите учетные данные для другого счета.\n", 2) + ACCOUNT_CREDS_FORMAT,
                reply_markup=InlineKeyboardMarkup(main_menu_decorator([])),
                parse_mode=ParseMode.MARKDOWN_V2
            )

            return UnblockUserConvStates.PROMPT_CREDENTIALS

        invite_links_service = InviteLinkService(session)
        link_by_account_num = invite_links_service.get_link_by_account_num(account_number)
        if link_by_account_num:
            logger.warning(f"[Unblock user] link with same account#, chat_id: {update.effective_chat.id}, username: {update.effective_user.username}, user_id {update.effective_user.id}")

            await context.bot.edit_message_text(
                chat_id=update.effective_chat.id,
                message_id=view_msg_id,
                text=helpers.escape_markdown("Приглашение с таким же номером счета уже существует. Введите учетные данные для другого счета.\n", 2) + ACCOUNT_CREDS_FORMAT,
                reply_markup=InlineKeyboardMarkup(main_menu_decorator([])),
                parse_mode=ParseMode.MARKDOWN_V2
            )

            return UnblockUserConvStates.PROMPT_CREDENTIALS

        valid_creds = mt5service.check_credentials(account_number, account_pwd)
        if not valid_creds:
            logger.warning(f"[Unblock user] invalid creds, chat_id: {update.effective_chat.id}, username: {update.effective_user.username}, user_id {update.effective_user.id}")

            await context.bot.edit_message_text(
                chat_id=update.effective_chat.id,
                message_id=view_msg_id,
                text=helpers.escape_markdown("Учетные данные недействительны. Попробуйте еще раз.\n", 2) + ACCOUNT_CREDS_FORMAT,
                reply_markup=InlineKeyboardMarkup(main_menu_decorator([])),
                parse_mode=ParseMode.MARKDOWN_V2
            )

            return UnblockUserConvStates.PROMPT_CREDENTIALS

        shared_user_id: int = context.user_data.get(UD_KEY_USER_ID_UNBLOCK)
        encrypted_pwd = pwd_encryptor.encrypt(account_pwd.encode())
        user_service.unblock_user(shared_user_id, account_number, encrypted_pwd)

        await context.bot.edit_message_text(
            chat_id=update.effective_chat.id,
            message_id=view_msg_id,
            text=helpers.escape_markdown(f"Учетные данные действительны.\nПользователь был разблокирован.", 2),
            reply_markup=InlineKeyboardMarkup(main_menu_decorator([])),
            parse_mode=ParseMode.MARKDOWN_V2
        )
        session.commit()

    # await send_main_menu(update, context)
    return UnblockUserConvStates.RESULT

# async def unblock_cancel(update: Update, context: ContextTypes.DEFAULT_TYPE):
#     logger.info(f"[Unblock user] cancel, chat_id: {update.effective_chat.id}, username: {update.effective_user.username}, user_id {update.effective_user.id}")
#     await update.message.reply_text(helpers.escape_markdown("Отмена генерации приглашения.", 2),
#                                     reply_markup=ReplyKeyboardRemove(), parse_mode=ParseMode.MARKDOWN_V2)
#     await send_main_menu(update, context)
#     return ConversationStates.MAIN_MENU


