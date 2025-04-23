import logging
from typing import List
from ptbcontrib.extract_passed_user import extract_passed_user

from sqlalchemy.orm.session import Session
from telegram import helpers, Update, ReplyKeyboardRemove, ReplyKeyboardMarkup, KeyboardButton, \
    KeyboardButtonRequestUsers
from telegram.constants import ParseMode
from telegram.ext import ContextTypes

from tg_server.constants.UserRole import UserRole
from tg_server.database.db import engine
from tg_server.handlers.commons import send_main_menu
from tg_server.handlers.states import InviteUserConvStates, ConversationStates
from tg_server.mt5.mt5service import mt5service
from tg_server.security.encryptor import pwd_encryptor
from tg_server.services.InviteLinkService import InviteLinkService
from tg_server.services.UserService import UserService

logger = logging.getLogger(__name__)

UD_KEY_USER_ID_TO_INVITE = "user_id_to_invite"

GENERATE_INVITE_PREFIX_MD2 = "*\[Генерация приглашения\]*\n"
USER_INVITE_PREFIX_MD2 = "*\[Приглашение\]*\n"

share_handle_msg = (GENERATE_INVITE_PREFIX_MD2
                    + helpers.escape_markdown("Пожалуйста, выберете пользователя, используя меню", 2)
                    )


# TODO: request_id ????
choose_user_markup = ReplyKeyboardMarkup(
    [[
        KeyboardButton("Выбрать пользователя",
                       request_users=KeyboardButtonRequestUsers(1, user_is_bot=False, request_username=True)),
    ]],
    one_time_keyboard=True, resize_keyboard=True)

async def invite_user_start(update: Update, context: ContextTypes.DEFAULT_TYPE):
    logger.info(f"[Invite start], chat_id: {update.effective_chat.id}, username: {update.effective_user.username}, user_id {update.effective_user.id}")

    await update.message.reply_text(
        share_handle_msg + "\n\nИспользуйте /cancel, чтобы вернуться в главное меню\.",
        reply_markup=choose_user_markup,
        parse_mode=ParseMode.MARKDOWN_V2,
    )

    return InviteUserConvStates.PROMPT_USER


async def invite_users_shared_invalid(update: Update, context: ContextTypes.DEFAULT_TYPE):
    logger.info(f"[Invite invalid user], chat_id: {update.effective_chat.id}, username: {update.effective_user.username}, user_id {update.effective_user.id}, message: {update.message.text}")
    return await invite_user_start(update, context)


prompt_creds_msg = (GENERATE_INVITE_PREFIX_MD2
                    + helpers.escape_markdown(
            "Пожалуйста, предоставьте данные учетной записи Gerchik в следующем формате:\n", 2)
                    + "```формат \<account_number\> \<password\> ```"
                    + "\n\n*__\(Сообщение с учетными данными будет удалено\)__*")


async def invite_users_shared(update: Update, context: ContextTypes.DEFAULT_TYPE):
    logger.info(f"[Invite user shared], chat_id: {update.effective_chat.id}, username: {update.effective_user.username}, user_id {update.effective_user.id}")
    shared_user = await extract_passed_user(message=update.message)

    if shared_user is None:
        return await invite_user_start(update, context)

    with Session(engine) as session:
        invite_link_service = InviteLinkService(session)
        link = invite_link_service.get_invite_link_by_id(shared_user.user_id)
        if link is not None:
            logger.warning(f"[Invite link exists], chat_id: {update.effective_chat.id}, username: {update.effective_user.username}, user_id {update.effective_user.id}, message: {update.message.text}")
            await update.message.reply_text(
                GENERATE_INVITE_PREFIX_MD2 + helpers.escape_markdown("У этого пользователя уже есть ожидающие приглашение (см. ниже). Выберите другого пользователя.", 2),
                reply_markup=choose_user_markup,
                parse_mode=ParseMode.MARKDOWN_V2,
                protect_content=True
            )
            await show_user_invite(update, context, link.code)
            return InviteUserConvStates.PROMPT_USER

        user_service = UserService(session)
        contains_user = user_service.contains_user(shared_user.user_id)
        if contains_user:
            logger.warning(f"[Invite user exists], chat_id: {update.effective_chat.id}, username: {update.effective_user.username}, user_id {update.effective_user.id}, message: {update.message.text}")
            await update.message.reply_text(
                GENERATE_INVITE_PREFIX_MD2 + helpers.escape_markdown("Этот пользователь уже зарегистрирован. Выберите другого пользователя.", 2),
                reply_markup=choose_user_markup,
                parse_mode=ParseMode.MARKDOWN_V2,
                protect_content=True
            )
            return InviteUserConvStates.PROMPT_USER

    # Save the shared user in user_data
    context.user_data[UD_KEY_USER_ID_TO_INVITE] = shared_user.user_id

    await update.message.reply_text(
        prompt_creds_msg,
        parse_mode=ParseMode.MARKDOWN_V2,
        reply_markup=ReplyKeyboardRemove(),
        protect_content=True
    )
    return InviteUserConvStates.PROMPT_CREDENTIALS

async def invite_invalid_account_credentials(update: Update, context: ContextTypes.DEFAULT_TYPE):
    logger.warning(f"[Invite invalid creds], chat_id: {update.effective_chat.id}, username: {update.effective_user.username}, user_id {update.effective_user.id}, message: {update.message.text}")
    await update.message.reply_text(
        GENERATE_INVITE_PREFIX_MD2 + helpers.escape_markdown("Неверный формат учетных данных. Попробуйте еще раз.", 2),
        parse_mode=ParseMode.MARKDOWN_V2,
        protect_content=True
    )
    return InviteUserConvStates.PROMPT_CREDENTIALS

async def invite_account_credentials(update: Update, context: ContextTypes.DEFAULT_TYPE):
    logger.info(f"[Invite creds entered], chat_id: {update.effective_chat.id}, username: {update.effective_user.username}, user_id {update.effective_user.id}")

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
        if user_with_same_account:
            logger.warning(f"[Invite user with same account#], chat_id: {update.effective_chat.id}, username: {update.effective_user.username}, user_id {update.effective_user.id}")
            await update.message.reply_text(
                GENERATE_INVITE_PREFIX_MD2 + helpers.escape_markdown("Пользователь с таким номером счета уже существует. Введите учетные данные для другого счета.", 2),
                parse_mode=ParseMode.MARKDOWN_V2,
                protect_content=True
            )
            return InviteUserConvStates.PROMPT_CREDENTIALS

        link_by_account_num = invite_links_service.get_link_by_account_num(account_number)
        if link_by_account_num:
            logger.warning(f"[Invite link with same account#], chat_id: {update.effective_chat.id}, username: {update.effective_user.username}, user_id {update.effective_user.id}")
            await update.message.reply_text(
                GENERATE_INVITE_PREFIX_MD2 + helpers.escape_markdown("Приглашение с таким же номером счета уже существует. Введите учетные данные для другого счета.", 2),
                parse_mode=ParseMode.MARKDOWN_V2,
                protect_content=True
            )
            return InviteUserConvStates.PROMPT_CREDENTIALS

        valid_creds = mt5service.check_credentials(account_number, account_pwd)
        if not valid_creds:
            logger.warning(f"[Invite invalid creds], chat_id: {update.effective_chat.id}, username: {update.effective_user.username}, user_id {update.effective_user.id}")
            await update.message.reply_text(
                GENERATE_INVITE_PREFIX_MD2 + helpers.escape_markdown("Учетные данные недействительны. Попробуйте еще раз.", 2),
                parse_mode=ParseMode.MARKDOWN_V2,
                protect_content=True
            )
            return InviteUserConvStates.PROMPT_CREDENTIALS

        await update.message.reply_text(
            GENERATE_INVITE_PREFIX_MD2 + helpers.escape_markdown(
                f"Учетные данные действительны.\nПерешлите следующее сообщение пользователю.\nГенерация приглашения...", 2),
            parse_mode=ParseMode.MARKDOWN_V2,
            protect_content=True
        )
        shared_user_id: int = context.user_data.get(UD_KEY_USER_ID_TO_INVITE)
        encrypted_pwd = pwd_encryptor.encrypt(account_pwd.encode())

        invite_code = invite_links_service.create_invite_link(update.effective_user.id, shared_user_id, UserRole.USER, account_number, encrypted_pwd)
        session.commit()

    await show_user_invite(update, context, invite_code)

    await send_main_menu(update, context)
    return ConversationStates.MAIN_MENU


async def show_user_invite(update, context, invite_code):
    deep_link = helpers.create_deep_linked_url(context.bot.username, invite_code)
    invite_link = f"[Нажмите здесь, чтобы присоединиться]({deep_link})"
    await update.message.reply_text(
        f"{USER_INVITE_PREFIX_MD2}{invite_link}",
        parse_mode=ParseMode.MARKDOWN_V2,
    )


async def invite_cancel(update: Update, context: ContextTypes.DEFAULT_TYPE):
    logger.warning(f"[Invite cancel], chat_id: {update.effective_chat.id}, username: {update.effective_user.username}, user_id {update.effective_user.id}")
    user = update.message.from_user
    logger.info("User %s canceled the conversation.", user.first_name)
    await update.message.reply_text(GENERATE_INVITE_PREFIX_MD2 + helpers.escape_markdown("Отмена генерации приглашения.", 2),
                                    reply_markup=ReplyKeyboardRemove(), parse_mode=ParseMode.MARKDOWN_V2)
    await send_main_menu(update, context)
    return ConversationStates.MAIN_MENU


