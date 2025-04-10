import logging
from typing import List

from sqlalchemy.orm.session import Session
from telegram import helpers, Update, ReplyKeyboardRemove, ReplyKeyboardMarkup, KeyboardButton, \
    KeyboardButtonRequestUsers, SharedUser
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

UD_KEY_USER_TO_INVITE = "user_to_invite"

GENERATE_INVITE_PREFIX_MD2 = "*\[Generate Invite\]*\n"
USER_INVITE_PREFIX_MD2 = "*\[User Invite\]*\n"

share_handle_msg = (GENERATE_INVITE_PREFIX_MD2
                    + helpers.escape_markdown("Please share the user using the menu", 2)
                    )


# TODO: request_id ????
choose_user_keyboard = [[
    KeyboardButton("Choose user",
                   request_users=KeyboardButtonRequestUsers(1, user_is_bot=False, request_username=True)),
]]

choose_user_markup = ReplyKeyboardMarkup(choose_user_keyboard, one_time_keyboard=True, resize_keyboard=True)

async def invite_start(update: Update, context: ContextTypes.DEFAULT_TYPE):
    """Starts the client invite process."""

    await update.message.reply_text(
        share_handle_msg + "\n\nUse /cancel command to go back to main menu\.",
        reply_markup=choose_user_markup,
        parse_mode=ParseMode.MARKDOWN_V2,
    )

    return InviteUserConvStates.PROMPT_USER


async def invite_users_shared_invalid(update: Update, context: ContextTypes.DEFAULT_TYPE):
    """Handle unexpected input from the user."""
    return await invite_start(update, context)


prompt_creds_msg = (GENERATE_INVITE_PREFIX_MD2
                    + helpers.escape_markdown(
            "Please share the Gerchik trade account credentials in the following format:\n", 2)
                    + "```credentials_format \<account_number\> \<password\> ```"
                    + "\n\n*__\(Message with credentials will be deleted\)__*")


async def invite_users_shared(update: Update, context: ContextTypes.DEFAULT_TYPE):
    """Saves shared tg users in user_data. Moves to the next state."""
    shared = update.message.users_shared
    if shared is None or len(shared.users) != 1:
        return await invite_start(update, context)

    shared_user = shared.users[0]

    with Session(engine) as session:
        invite_link_service = InviteLinkService(session)
        link = invite_link_service.get_invite_link_by_id(shared_user.user_id)
        if link is not None:
            await update.message.reply_text(
                GENERATE_INVITE_PREFIX_MD2 + helpers.escape_markdown("This user already has a pending invite link (see below). Please select another user.", 2),
                reply_markup=choose_user_markup,
                parse_mode=ParseMode.MARKDOWN_V2,
                protect_content=True
            )
            await show_user_invite(update, context, link.code)
            return InviteUserConvStates.PROMPT_USER

        user_service = UserService(session)
        contains_user = user_service.contains_user(shared_user.user_id)
        if contains_user:
            await update.message.reply_text(
                GENERATE_INVITE_PREFIX_MD2 + helpers.escape_markdown("This user is already registered. Please select another user.", 2),
                reply_markup=choose_user_markup,
                parse_mode=ParseMode.MARKDOWN_V2,
                protect_content=True
            )
            return InviteUserConvStates.PROMPT_USER

    # Save the shared user in user_data
    context.user_data[UD_KEY_USER_TO_INVITE] = shared_user

    await update.message.reply_text(
        prompt_creds_msg,
        parse_mode=ParseMode.MARKDOWN_V2,
        reply_markup=ReplyKeyboardRemove(),
        protect_content=True
    )
    return InviteUserConvStates.PROMPT_CREDENTIALS

async def invite_account_credentials(update: Update, context: ContextTypes.DEFAULT_TYPE):
    """ Deletes credentials message from user.
        Checks the credentials and saves them in the user_data. In case of invalid credentials: prompts again.
        Generates the invite link.
        Ends the conversation.
    """

    credentials_arr: List[str] = update.message.text.split(' ')

    account_number_str = credentials_arr[0]
    account_pwd = credentials_arr[1]

    await update.message.delete()

    valid_creds_format = account_number_str.isdigit()

    if not valid_creds_format:
        await update.message.reply_text(
            GENERATE_INVITE_PREFIX_MD2 + helpers.escape_markdown("Invalid credentials format. Please try again.", 2),
            parse_mode=ParseMode.MARKDOWN_V2,
            protect_content=True
        )
        return InviteUserConvStates.PROMPT_CREDENTIALS

    await update.message.reply_text(
        GENERATE_INVITE_PREFIX_MD2 + helpers.escape_markdown(f"Validating credentials...", 2),
        parse_mode=ParseMode.MARKDOWN_V2,
        protect_content=True
    )

    account_number = int(account_number_str)
    valid_creds = mt5service.check_credentials(account_number, account_pwd)

    if not valid_creds:
        await update.message.reply_text(
            GENERATE_INVITE_PREFIX_MD2 + helpers.escape_markdown("Credentials are not valid. Please try again.", 2),
            parse_mode=ParseMode.MARKDOWN_V2,
            protect_content=True
        )
        return InviteUserConvStates.PROMPT_CREDENTIALS

    await update.message.reply_text(
        GENERATE_INVITE_PREFIX_MD2 + helpers.escape_markdown(
            f"Credentials are valid.\nForward the next message to the user.\nGenerating invite link...", 2),
        parse_mode=ParseMode.MARKDOWN_V2,
        protect_content=True
    )
    shared_user: SharedUser = context.user_data.get(UD_KEY_USER_TO_INVITE)
    encrypted_pwd = pwd_encryptor.encrypt(account_pwd.encode())
    invite_code = save_invite_to_db(shared_user.user_id, update.effective_user.id, account_number, encrypted_pwd)

    await show_user_invite(update, context, invite_code)

    await send_main_menu(update, context)
    return ConversationStates.MAIN_MENU


async def show_user_invite(update, context, invite_code):
    deep_link = helpers.create_deep_linked_url(context.bot.username, invite_code)
    invite_link = f"[Click here to join]({deep_link})"
    await update.message.reply_text(
        f"{USER_INVITE_PREFIX_MD2}{invite_link}",
        parse_mode=ParseMode.MARKDOWN_V2,
    )

def save_invite_to_db(tg_userid_for, current_user_id, account_number: int, account_pwd_enc: bytes):
    with Session(engine) as session:
        invite_links_service = InviteLinkService(session)
        code = invite_links_service.create_invite_link(current_user_id, tg_userid_for, UserRole.USER, account_number, account_pwd_enc)
        session.commit()
        return code

async def invite_cancel(update: Update, context: ContextTypes.DEFAULT_TYPE):
    """Cancels and ends the conversation."""
    user = update.message.from_user
    logger.info("User %s canceled the conversation.", user.first_name)
    await update.message.reply_text(GENERATE_INVITE_PREFIX_MD2 + helpers.escape_markdown("Canceling invite generation.", 2),
                                    reply_markup=ReplyKeyboardRemove(), parse_mode=ParseMode.MARKDOWN_V2)
    await send_main_menu(update, context)
    return ConversationStates.MAIN_MENU


