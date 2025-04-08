import logging
import uuid

from telegram.constants import ParseMode
from telegram import Update, ReplyKeyboardMarkup, ReplyKeyboardRemove
from telegram.ext import filters, MessageHandler, ConversationHandler, ApplicationBuilder, CommandHandler, ContextTypes, PicklePersistence

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

SHARE_CONTACT, ACCOUNT_CREDENTIALS = range(2)

async def invite(update: Update, context: ContextTypes.DEFAULT_TYPE):
    """Starts the client invite process."""
    await update.message.reply_text(f"{GENERATE_INVITE_PREFIX_MD2}Please share the client's user handle with me\(e\.g\. \@username\)", parse_mode=ParseMode.MARKDOWN_V2)
    return SHARE_CONTACT

async def invite_share_handle(update: Update, context: ContextTypes.DEFAULT_TYPE):
    """Garbage"""
    await update.message.reply_text(f"{GENERATE_INVITE_PREFIX_MD2}Please share the account credentials in the format: <login> <password> *__(Message with credentials will be deleted)__*", parse_mode=ParseMode.MARKDOWN_V2 )
    return ACCOUNT_CREDENTIALS

async def invite_share_contact(update: Update, context: ContextTypes.DEFAULT_TYPE):
    """Garbage"""
    await update.message.reply_text(f"{GENERATE_INVITE_PREFIX_MD2}Please share the account credentials in the format: <login> <password> (Message with credentials will be deleted)", parse_mode=ParseMode.MARKDOWN_V2)
    return ACCOUNT_CREDENTIALS

async def invite_share_contact_invalid(update: Update, context: ContextTypes.DEFAULT_TYPE):
    """Garbage"""
    await update.message.reply_text(f"{GENERATE_INVITE_PREFIX_MD2}Please share the client's contact with me using the 'share contact' button'", parse_mode=ParseMode.MARKDOWN_V2)
    return SHARE_CONTACT

async def invite_account_credentials(update: Update, context: ContextTypes.DEFAULT_TYPE):
    """Checks the shared contact of the client"""
    invite_code = uuid.uuid4()
    # TODO: tg share link
    await update.message.delete()
    invite_link = f"[inline mention of a user](tg://user?id=123456789)"
    await update.message.reply_text(f"{GENERATE_INVITE_PREFIX_MD2}OK, I got the credentials Here is the invite link: {invite_code}", parse_mode=ParseMode.MARKDOWN_V2)
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

    # TODO: get from env variable
    application = (ApplicationBuilder()
                   .token('7137228876:AAEeBNncMzRUnV3NH9qzilrKjTsdDjm6aGs')
                   # .persistence(persistence=pickle_persistence)
                   .build()
                   )

    start_handler = CommandHandler('start', start)
    application.add_handler(start_handler)


    # TODO: conversation_timeout and allow_reentry ?
    # TODO: check the authorization of the user before proceeding
    invite_handler = ConversationHandler(
        entry_points=[CommandHandler("invite", invite)],
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
