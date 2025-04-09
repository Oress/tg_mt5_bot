import logging
import uuid
from datetime import datetime, timedelta
from typing import List

from sqlalchemy import text
from telegram import helpers, Update, ReplyKeyboardRemove, ReplyKeyboardMarkup, KeyboardButton, \
    KeyboardButtonRequestUsers, SharedUser
from telegram.constants import ParseMode
from telegram.ext import filters, MessageHandler, ConversationHandler, CommandHandler, ContextTypes

from tg_server.database.db import engine
from tg_server.handlers.filters.AuthFilter import manager_filter
from tg_server.security.encryptor import pwd_encryptor

logger = logging.getLogger(__name__)

UD_KEY_USER_TO_INVITE = "user_to_invite"

GENERATE_INVITE_PREFIX_MD2 = "*\[Generate Invite\]*\n"
USER_INVITE_PREFIX_MD2 = "*\[User Invite\]*\n"

SHARE_CONTACT, ACCOUNT_CREDENTIALS = range(2)

share_handle_msg = (GENERATE_INVITE_PREFIX_MD2
                    + helpers.escape_markdown("Please share the user using the menu", 2)
                    )


async def invite_start(update: Update, context: ContextTypes.DEFAULT_TYPE):
    """Starts the client invite process."""

    # TODO: request_id ????
    reply_keyboard = [[
        KeyboardButton("Choose user",
                       request_users=KeyboardButtonRequestUsers(1, user_is_bot=False, request_username=True)),
    ]]
    await update.message.reply_text(
        share_handle_msg + "\n\nUse /cancel command to go back to main menu\.",
        reply_markup=ReplyKeyboardMarkup(reply_keyboard, one_time_keyboard=True, resize_keyboard=True),
        parse_mode=ParseMode.MARKDOWN_V2,
    )

    return SHARE_CONTACT


async def invite_users_shared_invalid(update: Update, context: ContextTypes.DEFAULT_TYPE):
    """Handle unexpected input from the user."""
    return await invite_start(update, context)


share_creds_msg = (GENERATE_INVITE_PREFIX_MD2
                   + helpers.escape_markdown(
            "Please share the Gerchik trade account credentials in the following format:\n", 2)
                   + "```credentials_format \<login\> \<password\> ```"
                   + "\n\n*__\(Message with credentials will be deleted\)__*")


async def invite_users_shared(update: Update, context: ContextTypes.DEFAULT_TYPE):
    """Saves shared tg users in user_data. Moves to the next state."""
    shared = update.message.users_shared
    if shared is None or len(shared.users) != 1:
        return await invite_start(update, context)

    shared_user = shared.users[0]
    # Save the shared user in user_data
    context.user_data[UD_KEY_USER_TO_INVITE] = shared_user

    await update.message.reply_text(
        share_creds_msg,
        parse_mode=ParseMode.MARKDOWN_V2,
        reply_markup=ReplyKeyboardRemove(),
        protect_content=True
    )
    return ACCOUNT_CREDENTIALS


# TODO: check credentials
async def invite_account_credentials(update: Update, context: ContextTypes.DEFAULT_TYPE):
    """ Checks the credentials and saves them in user_data.
        Generates the invite link.
        Ends the conversation.
    """

    invite_code = str(uuid.uuid4())
    shared_user: SharedUser = context.user_data.get(UD_KEY_USER_TO_INVITE)
    print(shared_user)
    save_invite_to_db(invite_code, shared_user.user_id, update.effective_user.id)

    credentials_arr: List[str] = update.message.text.split(' ')

    account_login = credentials_arr[0]
    account_pwd = credentials_arr[0]
    encrypted_pwd = pwd_encryptor.encrypt(credentials_arr[1].encode())

    bot = context.bot
    deep_link = helpers.create_deep_linked_url(bot.username, invite_code)
    await update.message.delete()
    await update.message.reply_text(
        GENERATE_INVITE_PREFIX_MD2 + helpers.escape_markdown(
            f"OK, I got the credentials. Forward the next message to the user.", 2),
        parse_mode=ParseMode.MARKDOWN_V2,
        protect_content=True
    )

    invite_link = f"[Click here to join]({deep_link})"
    await update.message.reply_text(
        f"{USER_INVITE_PREFIX_MD2}{invite_link}",
        parse_mode=ParseMode.MARKDOWN_V2,
    )

    return ConversationHandler.END


def save_invite_to_db(invite_code, tg_userid_for, current_user_id):
    with engine.connect() as conn:
        datetime_now = datetime.now()
        expire = datetime_now + timedelta(minutes=30)
        now = datetime_now.strftime('%Y-%m-%d %H:%M:%S')
        exp = expire.strftime('%Y-%m-%d %H:%M:%S')

        conn.execute(
            text(
                "insert into tgb_invite_links (code, tg_userid_for, role_on_accept, created_by, created_at, expires_at)"
                " values (:code, :userid_for, :role, :by, :created_at, :exp_at)"),
            [{"code": invite_code, "userid_for": tg_userid_for, "role": "user", "by": current_user_id,
              "created_at": now, "exp_at": exp}],
        )
        conn.commit()


async def invite_cancel(update: Update, context: ContextTypes.DEFAULT_TYPE):
    """Cancels and ends the conversation."""
    user = update.message.from_user
    logger.info("User %s canceled the conversation.", user.first_name)
    await update.message.reply_text(GENERATE_INVITE_PREFIX_MD2 + helpers.escape_markdown("Canceling invite generation.", 2),
                                    reply_markup=ReplyKeyboardRemove(), parse_mode=ParseMode.MARKDOWN_V2)
    return ConversationHandler.END


# TODO: conversation_timeout and allow_reentry ?
# TODO: check the authorization of the user before proceeding
invite_handler = ConversationHandler(
    entry_points=[CommandHandler("invite", invite_start, filters=manager_filter)],
    states={
        SHARE_CONTACT: [
            CommandHandler("cancel", invite_cancel),
            MessageHandler(filters.StatusUpdate.USERS_SHARED, invite_users_shared),
            MessageHandler(~filters.StatusUpdate.USERS_SHARED, invite_users_shared_invalid),
        ],
        ACCOUNT_CREDENTIALS: [
            CommandHandler("cancel", invite_cancel),
            MessageHandler(filters.TEXT & ~filters.COMMAND, invite_account_credentials),
        ],
    },
    fallbacks=[CommandHandler("cancel", invite_cancel)],
)
