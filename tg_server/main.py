import asyncio
import logging
import os

from telegram import Update, Bot
from telegram.ext import filters, MessageHandler, ApplicationBuilder, ContextTypes, \
    PicklePersistence

from handlers.invite import invite_handler
from tg_server.constants.UserRole import UserRole
from tg_server.database.db import engine
from tg_server.handlers.start import start_handler
from tg_server.services.InviteLinkService import InviteLinkService
from tg_server.services.UserService import UserService

logging.basicConfig(
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s',
    level=logging.INFO
)

logger = logging.getLogger(__name__)


async def unknown_command(update: Update, context: ContextTypes.DEFAULT_TYPE):
    await context.bot.send_message(chat_id=update.effective_chat.id, text="Sorry, I didn't understand that command.")

# TODO: check if superadmin already exists
async def generate_superadmin_invite():
    superadmin_id = os.getenv("SUPERADMIN_ID")
    bot = Bot(tg_bot_token)
    async with bot:
        bot_user = await bot.get_me()
        with engine.connect() as conn:
            user_service = UserService(conn)
            invite_link_service = InviteLinkService(conn)

            superadmin_link = None
            if not user_service.contains_user(superadmin_id):
                superadmin_link = invite_link_service.create_invite_link(bot_user.username, None, superadmin_id, UserRole.SUPERADMIN, 30)

            conn.commit()
            if superadmin_link is None:
                logger.warning(f"Superadmin with id {superadmin_id} already exists")
            else:
                logger.info(f"Superadmin invite link: {superadmin_link}")
                print('Superadmin invite link:', superadmin_link)

if __name__ == '__main__':
    # TODO: use adequate 3rd party storage
    pickle_persistence = PicklePersistence(filepath='persistence.pickle')

    tg_bot_token = os.getenv("TG_BOT_TOKEN")

    application = (ApplicationBuilder()
                   .token(tg_bot_token)
                   # .persistence(persistence=pickle_persistence)
                   .build()
                   )

    application.add_handler(start_handler)
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
