import logging
import uuid

from telegram import helpers, Update, LinkPreviewOptions, ReplyKeyboardRemove
from telegram.constants import ParseMode
from telegram.ext import filters, MessageHandler, ConversationHandler, ApplicationBuilder, CommandHandler, ContextTypes, \
    PicklePersistence

logging.basicConfig(
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s',
    level=logging.INFO
)

logger = logging.getLogger(__name__)

# update - info about chat, user, message etc.
# context - info about the library? itself like Bot, Application, job_queue etc.
# the maximum length for the start parameter is 64.
async def start(update: Update, context: ContextTypes.DEFAULT_TYPE):
    print(context.args)
    if len(context.args) == 0:
        await context.bot.send_message(chat_id=update.effective_chat.id, text="Please use the invite link.")
        return

    invite_code = context.args[0]
    await context.bot.send_message(chat_id=update.effective_chat.id, text="I'm a bot, please talk to me!")


GENERATE_INVITE_PREFIX_MD2 = "*\[Generate Invite\]*\n"
INVITE_PREFIX_MD2 = "*\[Invite\]*\n"

SHARE_CONTACT, ACCOUNT_CREDENTIALS = range(2)

share_handle_msg = (GENERATE_INVITE_PREFIX_MD2
                    + helpers.escape_markdown("Please share the client's handle (e.g. @username)", 2)
                    )

async def invite_start(update: Update, context: ContextTypes.DEFAULT_TYPE):
    """Starts the client invite process."""
    await update.message.reply_text(
        share_handle_msg + "\n\nUse /cancel command to go back to main menu\.",
        parse_mode=ParseMode.MARKDOWN_V2,
        protect_content=True
    )
    return SHARE_CONTACT

async def invite_share_contact_invalid(update: Update, context: ContextTypes.DEFAULT_TYPE):
    """Garbage"""
    await update.message.reply_text(
        share_handle_msg,
        parse_mode=ParseMode.MARKDOWN_V2,
        protect_content=True
    )
    return SHARE_CONTACT


share_creds_msg = (GENERATE_INVITE_PREFIX_MD2
                   + helpers.escape_markdown("Please share the account credentials in the following format:\n<login> <password>", 2)
                   + "\n\n*__\(Message with credentials will be deleted\)__*")

async def invite_share_handle(update: Update, context: ContextTypes.DEFAULT_TYPE):
    """Garbage"""
    await update.message.reply_text(
        share_creds_msg,
        parse_mode=ParseMode.MARKDOWN_V2,
        protect_content=True
    )
    return ACCOUNT_CREDENTIALS

async def invite_share_contact(update: Update, context: ContextTypes.DEFAULT_TYPE):
    """Garbage"""
    await update.message.reply_text(
        share_creds_msg,
        parse_mode=ParseMode.MARKDOWN_V2,
        protect_content=True
    )
    return ACCOUNT_CREDENTIALS

# TODO: check credentials
async def invite_account_credentials(update: Update, context: ContextTypes.DEFAULT_TYPE):
    """Checks the shared contact of the client"""
    invite_code = uuid.uuid4()
    bot = context.bot

    deep_link = helpers.create_deep_linked_url(bot.username, str(invite_code))
    await update.message.delete()
    await update.message.reply_text(
        GENERATE_INVITE_PREFIX_MD2 + helpers.escape_markdown("OK, I got the credentials. Forward the next message to the @user_from_prev_msg. ", 2),
        parse_mode=ParseMode.MARKDOWN_V2,
        protect_content=True
    )

    invite_link = f"[Click here to join]({deep_link})"
    await update.message.reply_text(
        f"{INVITE_PREFIX_MD2}{invite_link}",
        parse_mode=ParseMode.MARKDOWN_V2,
        link_preview_options=LinkPreviewOptions(is_disabled=True)
    )

    return ConversationHandler.END

async def invite_cancel(update: Update, context: ContextTypes.DEFAULT_TYPE):
    """Cancels and ends the conversation."""
    user = update.message.from_user
    logger.info("User %s canceled the conversation.", user.first_name)
    await update.message.reply_text(f"{GENERATE_INVITE_PREFIX_MD2}Canceling invite generation", reply_markup=ReplyKeyboardRemove(), parse_mode=ParseMode.MARKDOWN_V2)
    return ConversationHandler.END

#
# async def menu_command(update: Update, context: ContextTypes.DEFAULT_TYPE):
#     reply_keyboard = [["Boy", "Girl", "Other"]]
#
#     await update.message.reply_text(
#         "Hi! My name is Professor Bot. I will hold a conversation with you. "
#         "Send /cancel to stop talking to me.\n\n"
#         "Are you a boy or a girl?",
#         reply_markup=ReplyKeyboardMarkup(
#             reply_keyboard, one_time_keyboard=True, input_field_placeholder="Boy or Girl?"
#         ),
#     )


async def unknown_command(update: Update, context: ContextTypes.DEFAULT_TYPE):
    await context.bot.send_message(chat_id=update.effective_chat.id, text="Sorry, I didn't understand that command.")

if __name__ == '__main__':
    # TODO: use adequate 3rd party storage
    pickle_persistence = PicklePersistence(filepath='persistence.pickle')

    # TODO: regenerate token this one is already compromised.
    tg_token = '7137228876:AAEeBNncMzRUnV3NH9qzilrKjTsdDjm6aGs'

    # TODO: get from env variable
    application = (ApplicationBuilder()
                   .token(tg_token)
                   # .persistence(persistence=pickle_persistence)
                   .build()
                   )

    start_handler = CommandHandler('start', start)
    application.add_handler(start_handler)


    # TODO: conversation_timeout and allow_reentry ?
    # TODO: check the authorization of the user before proceeding
    invite_handler = ConversationHandler(
        entry_points=[CommandHandler("invite", invite_start)],
        states={
            SHARE_CONTACT: [
                CommandHandler("cancel", invite_cancel),
                # MessageHandler(filters.CONTACT, invite_share_contact),
                # TODO: I need to validate a tg handle, it's not possible with default API methods
                MessageHandler(filters.Regex("^@(?=.{5,64}(?:\s|$))(?![_])(?!.*[_]{2})[a-zA-Z0-9_]+(?<![_.])$"), invite_share_handle),
                MessageHandler(~filters.CONTACT, invite_share_contact_invalid),
            ],
            ACCOUNT_CREDENTIALS: [
                CommandHandler("cancel", invite_cancel),
                MessageHandler(filters.TEXT & ~filters.COMMAND, invite_account_credentials),
            ],
        },
        fallbacks=[CommandHandler("cancel", invite_cancel)],
    )
    application.add_handler(invite_handler)

    # echo_handler = MessageHandler(filters.TEXT & (~filters.COMMAND), echo)
    # application.add_handler(echo_handler)

    # # Sends a keyboard menu options to the user
    # menu_handler = CommandHandler('menu', menu_command)
    # application.add_handler(menu_handler)

    # This handler must be added last
    unknown_handler = MessageHandler(filters.COMMAND, unknown_command)
    application.add_handler(unknown_handler)

    application.run_polling()

# # import the package
# from mt5linux import MetaTrader5
#
# # connecto to the server
# mt5 = MetaTrader5(
#     # host = 'localhost' (default)
#     # port = 18812       (default)
#     port=8001
# )
# mt5.initialize()
# print(mt5.version())
#
# mt5.shutdown()
#
