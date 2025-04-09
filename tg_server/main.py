import logging
import asyncio
import os

from telegram import Update, Bot
from telegram.ext import filters, MessageHandler, ApplicationBuilder, CommandHandler, ContextTypes, \
    PicklePersistence

from handlers.invite import invite_handler
from services.InviteLinkService import invite_link_service
from tg_server.constants.UserRole import UserRole

logging.basicConfig(
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s',
    level=logging.INFO
)

logger = logging.getLogger(__name__)

async def start(update: Update, context: ContextTypes.DEFAULT_TYPE):
    if len(context.args) == 0:
        await context.bot.send_message(chat_id=update.effective_chat.id, text="Please use the invite link.")
        return

    user_id = update.effective_user.id
    invite_code = context.args[0]

    if invite_link_service.validate_invite_link(user_id, invite_code) is True:
        await context.bot.send_message(chat_id=update.effective_chat.id, text="Welcome!")
    else:
        await context.bot.send_message(chat_id=update.effective_chat.id, text="Invalid invite link.")


async def unknown_command(update: Update, context: ContextTypes.DEFAULT_TYPE):
    await context.bot.send_message(chat_id=update.effective_chat.id, text="Sorry, I didn't understand that command.")

# TODO: check if superadmin already exists
async def generate_superadmin_invite():
    superadmin_id = os.getenv("SUPERADMIN_ID")
    bot = Bot(tg_bot_token)
    async with bot:
        bot_user = await bot.get_me()
        superadmin_link = invite_link_service.create_invite_link(bot_user.username, None, superadmin_id, UserRole.SUPERADMIN, 30)
        print(superadmin_link)

if __name__ == '__main__':
    # TODO: use adequate 3rd party storage
    pickle_persistence = PicklePersistence(filepath='persistence.pickle')

    # TODO: regenerate token this one is already compromised.
    tg_bot_token = os.getenv("TG_BOT_TOKEN")

    # TODO: get from env variable
    application = (ApplicationBuilder()
                   .token(tg_bot_token)
                   # .persistence(persistence=pickle_persistence)
                   .build()
                   )

    start_handler = CommandHandler('start', start)
    application.add_handler(start_handler)

    # TODO: conversation_timeout and allow_reentry ?
    # TODO: check the authorization of the user before proceeding

    application.add_handler(invite_handler)

    # # Sends a keyboard menu options to the user
    # menu_handler = CommandHandler('menu', menu_command)
    # application.add_handler(menu_handler)

    # This handler must be added last
    unknown_handler = MessageHandler(filters.COMMAND, unknown_command)
    application.add_handler(unknown_handler)

    temp_el = asyncio.new_event_loop()
    try:
        temp_el.run_until_complete(generate_superadmin_invite())
        temp_el.close()
    finally:
        temp_el.close()

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
