import asyncio
import logging
import os
from logging.handlers import RotatingFileHandler

from sqlalchemy.orm.session import Session
from telegram import helpers, Update, Bot
from telegram.ext import filters, MessageHandler, ApplicationBuilder, ContextTypes, ConversationHandler, CommandHandler

from handlers.invite import invite_user_start, invite_cancel, invite_users_shared, \
    invite_users_shared_invalid, invite_account_credentials, invite_invalid_account_credentials
from tg_server.constants.UserRole import UserRole
from tg_server.database.db import engine
from tg_server.handlers.balance import balance_command
from tg_server.handlers.filters.AuthFilter import manager_filter, user_filter, admin_filter
from tg_server.handlers.hedge import hedge_start, hedge_operation_selected, hedge_instrument_selected, \
    hedge_volume_selected, hedge_city_selected, hedge_preview_accepted, hedge_invalid_operation_selected, inst_list, \
    hedge_cancel, hedge_invalid_instrument_selected, hedge_invalid_volume_selected, cities_list, \
    hedge_invalid_city_selected, hedge_invalid_preview_choice
from tg_server.handlers.help import help_command_admin, help_command_user, help_command_unauthorized
from tg_server.handlers.invite_admin import invite_admin_start, invite_admin_cancel, invite_admin_shared, \
    invite_admin_shared_invalid
from tg_server.handlers.start import start_command
from tg_server.handlers.states import ConversationStates, InviteUserConvStates, HedgeConvStates, InviteAdminConvStates
from tg_server.services.InviteLinkService import InviteLinkService
from tg_server.services.UserService import UserService

from sqlalchemy.orm import sessionmaker, scoped_session

from ptbcontrib.postgres_persistence import PostgresPersistence

logging.basicConfig(
    format="[%(asctime)s] %(levelname)s [%(name)s.%(funcName)s:%(lineno)d] %(message)s",
    datefmt='%Y-%m-%dT%H:%M:%S',
    level=logging.INFO,
    handlers=[
        logging.StreamHandler(),
        RotatingFileHandler("app.log", maxBytes=5 * 1024 * 1024, backupCount=5)
    ],
)

logger = logging.getLogger(__name__)

def check_env_variables():
    required_env_vars = [
        "ENCRYPTION_KEY",

        "MIGRATION_DB_HOST",
        "MIGRATION_DB_PORT",
        "POSTGRES_USER",
        "POSTGRES_PASSWORD",
        "POSTGRES_DB",

        "MT5_HOST",
        "MT5_PORT",
        "MT5_SERVER",

        "HEDGE_INSTRUMENTS",
        "HEDGE_CITIES",

        "TG_BOT_TOKEN",
        "SUPERADMIN_ID"
    ]
    missing_vars = [var for var in required_env_vars if os.getenv(var) is None or os.getenv(var) == '']
    if missing_vars:
        logger.error(f"[Env. Validation]Missing environment variables: {', '.join(missing_vars)}")
        raise EnvironmentError(f"Missing environment variables: {', '.join(missing_vars)}")

async def remove_expired_links(context: ContextTypes.DEFAULT_TYPE):
    logger.info("[Job|Remove Expired Links] Removing expired invite links")
    with Session(engine) as session:
        invite_link_service = InviteLinkService(session)
        invite_link_service.remove_expired_links()
        session.commit()


async def edit_unsupported_command(update: Update, context: ContextTypes.DEFAULT_TYPE):
    logger.warning(f"[Msg edit], ignoring it, chat_id: {update.effective_chat.id}, message: {update.effective_message.text}, username: {update.effective_user.username}, user_id {update.effective_user.id}")
    await context.bot.send_message(chat_id=update.effective_chat.id, text="Message editing is not supported. Please use the command again.")

async def unknown_command(update: Update, context: ContextTypes.DEFAULT_TYPE):
    logger.warning(f"[Unknown command], ignoring it, chat_id: {update.effective_chat.id}, message: {update.effective_message.text}, username: {update.effective_user.username}, user_id {update.effective_user.id}")
    await context.bot.send_message(chat_id=update.effective_chat.id, text="Sorry, I didn't understand that command.")

async def unexpected_text(update: Update, context: ContextTypes.DEFAULT_TYPE):
    logger.warning(f"[Unknown text], ignoring it, chat_id: {update.effective_chat.id}, message: {update.effective_message.text}, username: {update.effective_user.username}, user_id {update.effective_user.id}")
    await context.bot.send_message(chat_id=update.effective_chat.id, text="Sorry, I don't understand what are you trying to do. Use /help to see the list of available commands.")

async def generate_superadmin_invite():
    superadmin_id = int(os.getenv("SUPERADMIN_ID"))
    bot = Bot(tg_bot_token)
    async with bot:
        with Session(engine) as session:
            user_service = UserService(session)
            invite_link_service = InviteLinkService(session)

            superadmin_link = None
            if not user_service.contains_user(superadmin_id):
                code = invite_link_service.create_invite_link(None, superadmin_id, UserRole.SUPERADMIN)
                superadmin_link = helpers.create_deep_linked_url(bot.username, code)

            session.commit()
            if superadmin_link is None:
                logger.info(f"[Superadmin init]Superadmin with id {superadmin_id} already exists")
            else:
                print('Superadmin invite link:', superadmin_link)

def start_session() -> scoped_session:
    return scoped_session(sessionmaker(bind=engine, autoflush=False))

def disable_edited_msg(filter=None):
    return filter & ~filters.UpdateType.EDITED_MESSAGE if filter else ~filters.UpdateType.EDITED_MESSAGE

if __name__ == '__main__':
    check_env_variables()
    tg_bot_token = os.getenv("TG_BOT_TOKEN")

    application = (ApplicationBuilder()
                   .token(tg_bot_token)
                   .persistence(PostgresPersistence(session=start_session()))
                   .build()
                   )

    job_queue = application.job_queue
    job_minute = job_queue.run_repeating(remove_expired_links, interval=30 * 60, first=10)

    # TODO: conversation_timeout and allow_reentry ?
    invite_user_conv = ConversationHandler(
        name="invite_user_conversation",
        persistent=True,
        entry_points=[
            CommandHandler("inviteuser", invite_user_start, filters=disable_edited_msg(manager_filter)),
            MessageHandler(disable_edited_msg(filters.Text(["Invite User"]) & manager_filter), invite_user_start)
        ],
        states={
            InviteUserConvStates.PROMPT_USER: [
                CommandHandler("cancel", invite_cancel, filters=disable_edited_msg()),
                MessageHandler(disable_edited_msg(filters.StatusUpdate.USERS_SHARED), invite_users_shared),
                MessageHandler(disable_edited_msg(~filters.StatusUpdate.USERS_SHARED), invite_users_shared_invalid),
            ],
            InviteUserConvStates.PROMPT_CREDENTIALS: [
                CommandHandler("cancel", invite_cancel, filters=disable_edited_msg()),
                MessageHandler(disable_edited_msg(filters.Regex("^\d+ \S+$")), invite_account_credentials),
                MessageHandler(disable_edited_msg(~filters.Regex("^\d+ \S+$")), invite_invalid_account_credentials),
            ],
        },
        fallbacks=[],
        map_to_parent={
            ConversationStates.MAIN_MENU: ConversationStates.MAIN_MENU,
        }
    )

    invite_admin_conv = ConversationHandler(
        name="invite_admin_conversation",
        persistent=True,
        entry_points=[
            CommandHandler("inviteadmin", invite_admin_start, filters=disable_edited_msg(admin_filter)),
            MessageHandler(disable_edited_msg(filters.Text(["Invite Admin"]) & admin_filter), invite_admin_start)
        ],
        states={
            InviteAdminConvStates.PROMPT_USER: [
                CommandHandler("cancel", invite_admin_cancel, filters=disable_edited_msg()),
                MessageHandler(disable_edited_msg(filters.StatusUpdate.USERS_SHARED), invite_admin_shared),
                MessageHandler(disable_edited_msg(~filters.StatusUpdate.USERS_SHARED), invite_admin_shared_invalid),
            ],
        },
        fallbacks=[],
        map_to_parent={
            ConversationStates.MAIN_MENU: ConversationStates.MAIN_MENU,
        }
    )

    cancel_command_handler = CommandHandler("cancel", hedge_cancel, filters=disable_edited_msg())

    hedge_conv = ConversationHandler(
        name="hedge_conversation",
        persistent=True,
        entry_points=[
            CommandHandler("hedge", hedge_start, filters=disable_edited_msg(user_filter)),
            MessageHandler(disable_edited_msg(filters.Text(["Hedge"]) & user_filter), hedge_start)
        ],
        states={
            HedgeConvStates.SELECT_OPERATION: [
                cancel_command_handler,
                MessageHandler(disable_edited_msg(filters.Text(["Buy", "Sell"])), hedge_operation_selected),
                MessageHandler(disable_edited_msg(~filters.Text(["Buy", "Sell"])), hedge_invalid_operation_selected),
            ],
            HedgeConvStates.SELECT_INSTRUMENT: [
                cancel_command_handler,
                MessageHandler(disable_edited_msg(filters.Text(inst_list)), hedge_instrument_selected),
                MessageHandler(disable_edited_msg(~filters.Text(inst_list)), hedge_invalid_instrument_selected)
            ],
            HedgeConvStates.SELECT_VOLUME: [
                cancel_command_handler,
                MessageHandler(disable_edited_msg(filters.Regex("^[1-9]\d*(\.\d{1,2})?$|^0\.\d{1,2}$")), hedge_volume_selected),
                MessageHandler(disable_edited_msg(~filters.Regex("^[1-9]\d*(\.\d{1,2})?$|^0\.\d{1,2}$")), hedge_invalid_volume_selected)
            ],
            HedgeConvStates.SELECT_CITY: [
                cancel_command_handler,
                MessageHandler(disable_edited_msg(filters.Text(cities_list)), hedge_city_selected),
                MessageHandler(disable_edited_msg(~filters.Text(cities_list)), hedge_invalid_city_selected)
            ],
            HedgeConvStates.PREVIEW: [
                cancel_command_handler,
                MessageHandler(disable_edited_msg(filters.Text(["Place order"])), hedge_preview_accepted),
                MessageHandler(disable_edited_msg(filters.Text(["Edit"])), hedge_start),
                MessageHandler(disable_edited_msg(~filters.Text(["Place order", "Edit"])), hedge_invalid_preview_choice),
            ],
        },
        fallbacks=[],
        map_to_parent={
            ConversationStates.MAIN_MENU: ConversationStates.MAIN_MENU,
        }
    )

    conv_handler = ConversationHandler(
        name="menu_conversation",
        persistent=True,
        entry_points=[CommandHandler(["start", "menu"], start_command, filters=disable_edited_msg())],
        states = {
            ConversationStates.MAIN_MENU: [
                CommandHandler("balance", balance_command, filters=disable_edited_msg()),
                MessageHandler(disable_edited_msg(filters.Text(["Balance"]) & user_filter), balance_command),
                invite_user_conv,
                invite_admin_conv,
                hedge_conv,
                CommandHandler(["start", "menu"], start_command, filters=disable_edited_msg()),
            ],
        },
        fallbacks=[],
    )

    application.add_handler(conv_handler)
    application.add_handler(hedge_conv)

    application.add_handler(CommandHandler("balance", balance_command, filters=disable_edited_msg(user_filter)))
    application.add_handler(MessageHandler(disable_edited_msg(filters.Text(["Balance"]) & user_filter), hedge_start))

    application.add_handler(CommandHandler("help", help_command_admin, filters=disable_edited_msg(admin_filter)))
    application.add_handler(CommandHandler("help", help_command_user, filters=disable_edited_msg(user_filter)))
    application.add_handler(CommandHandler("help", help_command_unauthorized, filters=disable_edited_msg()))

    # These handlers must be added last
    application.add_handler(MessageHandler(filters.UpdateType.EDITED_MESSAGE, edit_unsupported_command))
    application.add_handler(MessageHandler(filters.COMMAND, unknown_command))
    application.add_handler(MessageHandler(filters.TEXT, unexpected_text))

    temp_el = asyncio.new_event_loop()
    try:
        temp_el.run_until_complete(generate_superadmin_invite())
        temp_el.close()
    finally:
        temp_el.close()

    application.run_polling()
