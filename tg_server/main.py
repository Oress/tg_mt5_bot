import asyncio
import logging
import os
from logging.handlers import RotatingFileHandler
from warnings import filterwarnings

from ptbcontrib.postgres_persistence import PostgresPersistence
from sqlalchemy.orm import sessionmaker, scoped_session
from sqlalchemy.orm.session import Session
from telegram import helpers, Update, Bot, InlineKeyboardMarkup
from telegram.constants import ParseMode
from telegram.ext import filters, MessageHandler, CallbackQueryHandler, ApplicationBuilder, ContextTypes, \
    ConversationHandler, CommandHandler

from handlers.invite import invite_user_start, invite_users_shared, \
    invite_account_credentials
from tg_server.constants.UserRole import UserRole
from tg_server.database.db import engine
from tg_server.handlers.balance import balance_command
from tg_server.handlers.block_user import block_user_start, block_user_shared, \
    block_user_reason_handler
from tg_server.handlers.change_creds import change_creds_start, change_creds_shared, \
    change_creds_account_credentials
from tg_server.handlers.commons import main_menu_callback, PLACE_ORDER, PREVIEW_EDIT, HEDGE, BALANCE, UNBLOCK_USER, \
    INVITE_USER, BLOCK_USER, CHANGE_USER_ACCOUNT_CREDS, MAIN_MENU, \
    main_menu_command, main_menu_decorator, UD_KEY_MENU_MSG_ID, BLOCK_USER_SKIP_RSN, INVITE_ADMIN
from tg_server.handlers.filters.AuthFilter import user_filter, BlockedUserFilter
from tg_server.handlers.hedge import hedge_start, hedge_operation_selected, hedge_instrument_selected, \
    hedge_volume_selected, hedge_preview_accepted
from tg_server.handlers.help import help_command_user, help_command_unauthorized
from tg_server.handlers.invite_admin import invite_admin_shared, invite_admin_start
from tg_server.handlers.start import start_command
from tg_server.handlers.states import ConversationStates, InviteUserConvStates, HedgeConvStates, InviteAdminConvStates, \
    ChangeCredsConvStates, BlockUserConvStates, UnblockUserConvStates, BalanceConvStates
from tg_server.handlers.unblock_user import unblock_user_start, unblock_users_shared, \
    unblock_account_credentials
from tg_server.services.InviteLinkService import InviteLinkService
from tg_server.services.UserService import UserService
from tg_server.sheets.SheetsSync import sync_with_google_sheets
from telegram.warnings import PTBUserWarning

# https://github.com/python-telegram-bot/python-telegram-bot/wiki/Frequently-Asked-Questions#what-do-the-per_-settings-in-conversationhandler-do
filterwarnings(action="ignore", message=r".*CallbackQueryHandler", category=PTBUserWarning)

def check_env_variables():
    required_env_vars = [
        "LOGS_FOLDER",

        "ENCRYPTION_KEY",

        "POSTGRES_DB_HOST",
        "POSTGRES_DB_PORT",
        "POSTGRES_USER",
        "POSTGRES_PASSWORD",
        "POSTGRES_DB",

        "MT5_HOST",
        "MT5_PORT",
        "MT5_SERVER",

        "HEDGE_CITIES",

        "GGL_SHEET_SYNC_INVTERVAL",
        "GGL_SPREADSHEET_ID",
        "GGL_SA_FILE_LOCATION",

        "TG_BOT_TOKEN",
        "SUPERADMIN_ID"
    ]
    missing_vars = [var for var in required_env_vars if os.getenv(var) is None or os.getenv(var) == '']
    if missing_vars:
        raise EnvironmentError(f"Missing environment variables: {', '.join(missing_vars)}")

check_env_variables()

logs_folder = os.getenv("LOGS_FOLDER")
os.makedirs(logs_folder, exist_ok=True)
logging.basicConfig(
    format="[%(asctime)s] %(levelname)s [%(name)s.%(funcName)s:%(lineno)d] %(message)s",
    datefmt='%Y-%m-%dT%H:%M:%S',
    level=logging.INFO,
    handlers=[
        logging.StreamHandler(),
        RotatingFileHandler(logs_folder + "/app.log", maxBytes=5 * 1024 * 1024, backupCount=5)
    ],
)

logger = logging.getLogger(__name__)

async def remove_expired_links(context: ContextTypes.DEFAULT_TYPE):
    logger.info("[Job|Remove Expired Links] Removing expired invite links")
    with Session(engine) as session:
        invite_link_service = InviteLinkService(session)
        invite_link_service.remove_expired_links()
        session.commit()


async def user_has_been_blocked(update: Update, context: ContextTypes.DEFAULT_TYPE):
    logger.warning(f"[User is blocked], ignoring request, chat_id: {update.effective_chat.id}, message: {update.effective_message.text}, username: {update.effective_user.username}, user_id {update.effective_user.id}")
    await context.bot.send_message(chat_id=update.effective_chat.id, text="Вы были заблокированы.")

async def edit_unsupported_command(update: Update, context: ContextTypes.DEFAULT_TYPE):
    logger.warning(f"[Msg edit], ignoring it, chat_id: {update.effective_chat.id}, message: {update.effective_message.text}, username: {update.effective_user.username}, user_id {update.effective_user.id}")
    await context.bot.send_message(chat_id=update.effective_chat.id, text="Редактирование сообщений не поддерживается. Пожалуйста, используйте команду еще раз.")

async def unknown_command(update: Update, context: ContextTypes.DEFAULT_TYPE):
    logger.warning(f"[Unknown command], ignoring it, chat_id: {update.effective_chat.id}, message: {update.effective_message.text}, username: {update.effective_user.username}, user_id {update.effective_user.id}")
    # await context.bot.send_message(chat_id=update.effective_chat.id, text="Извините, я не понял эту команду.")
    await context.bot.delete_message(chat_id=update.effective_chat.id, message_id=update.effective_message.message_id)

async def unexpected_text(update: Update, context: ContextTypes.DEFAULT_TYPE):
    logger.warning(f"[Unknown text], ignoring it, chat_id: {update.effective_chat.id}, message: {update.effective_message.text}, username: {update.effective_user.username}, user_id {update.effective_user.id}")
    # await context.bot.send_message(chat_id=update.effective_chat.id, text="Извините, я не понимаю что вы пытаетесь сделать. Используйте /help, чтобы увидеть список доступных команд.")
    await context.bot.delete_message(chat_id=update.effective_chat.id, message_id=update.effective_message.message_id)

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
                print('[Superadmin init] Superadmin invite link:', superadmin_link)

def start_session() -> scoped_session:
    return scoped_session(sessionmaker(bind=engine, autoflush=False))

def disable_edited_msg(filter=None):
    return filter & ~filters.UpdateType.EDITED_MESSAGE if filter else ~filters.UpdateType.EDITED_MESSAGE

async def global_error_handle(update: Update, context: ContextTypes.DEFAULT_TYPE):
    if update:
        logger.error(f"[Global error handler] Error: {context.error}, chat_id: {update.effective_chat.id}, message: {update.effective_message.text}, username: {update.effective_user.username}, user_id {update.effective_user.id}" , exc_info=context.error)
        view_msg_id = context.user_data.get(UD_KEY_MENU_MSG_ID)

        if view_msg_id is not None:
            try:
                await context.bot.edit_message_text(
                    chat_id=update.effective_chat.id,
                    message_id=view_msg_id,
                    text=helpers.escape_markdown("Произошла ошибка. Пожалуйста, попробуйте позже.", 2),
                    reply_markup=InlineKeyboardMarkup(main_menu_decorator([])),
                    parse_mode=ParseMode.MARKDOWN_V2
                )
            except Exception as e:
                context.user_data.pop(UD_KEY_MENU_MSG_ID)
                logger.error(f"[Start Registered User] Error deleting message: {e}")
        else:
            logger.error(f"[Global error handler] Error: {context.error}")
    elif context:
        logger.error(f"[Global error handler] Error: {context.error}", exc_info=context.error)


if __name__ == '__main__':
    tg_bot_token = os.getenv("TG_BOT_TOKEN")

    application = (ApplicationBuilder()
                   .token(tg_bot_token)
                   .persistence(PostgresPersistence(session=start_session()))
                   .build()
                   )

    application.add_error_handler(global_error_handle)

    sync_interval = int(os.getenv("GGL_SHEET_SYNC_INVTERVAL"))
    job_queue = application.job_queue
    job_queue.run_repeating(remove_expired_links, interval=30 * 60, first=10)
    job_queue.run_repeating(sync_with_google_sheets, interval=sync_interval * 60, first=5)


    main_menu_text_filter = filters.Text([MAIN_MENU.label])
    start_menu_handler = CommandHandler(["start", "menu"], main_menu_command, filters=disable_edited_msg())
    main_menu_query_handler = CallbackQueryHandler(main_menu_callback, MAIN_MENU.callback_data)

    balance_conv = ConversationHandler(
        name="balance_user_conversation",
        persistent=True,
        entry_points=[
            CallbackQueryHandler(balance_command, BALANCE.callback_data),
        ],
        states={
            BalanceConvStates.BALANCE_OPENED: [
                start_menu_handler,
                main_menu_query_handler
            ],
        },
        fallbacks=[],
        map_to_parent={
            ConversationStates.MAIN_MENU: ConversationStates.MAIN_MENU,
        }
    )

    change_creds_conv = ConversationHandler(
        name="change_creds_conversation",
        persistent=True,
        entry_points=[
            CallbackQueryHandler(change_creds_start, CHANGE_USER_ACCOUNT_CREDS.callback_data),
            # CommandHandler("changecreds", change_creds_start, filters=disable_edited_msg(manager_filter)),
            # MessageHandler(disable_edited_msg(filters.Text([CHANGE_USER_ACCOUNT_CREDS.label]) & manager_filter), change_creds_start)
        ],
        states={
            ChangeCredsConvStates.PROMPT_USER: [
                start_menu_handler,
                main_menu_query_handler,
                MessageHandler(disable_edited_msg(filters.StatusUpdate.USERS_SHARED), change_creds_shared),
            ],
            ChangeCredsConvStates.PROMPT_CREDENTIALS: [
                start_menu_handler,
                main_menu_query_handler,
                MessageHandler(disable_edited_msg(filters.Regex("^\d+ \S+$")), change_creds_account_credentials),
            ],
            ChangeCredsConvStates.RESULT: [
                start_menu_handler,
                main_menu_query_handler,
            ]
        },
        fallbacks=[],
        map_to_parent={
            ConversationStates.MAIN_MENU: ConversationStates.MAIN_MENU,
        }
    )

    block_user_conv = ConversationHandler(
        name="block_user_conversation",
        persistent=True,
        entry_points=[
            CallbackQueryHandler(block_user_start, BLOCK_USER.callback_data),
        ],
        states={
            BlockUserConvStates.PROMPT_USER: [
                start_menu_handler,
                main_menu_query_handler,
                MessageHandler(disable_edited_msg(filters.StatusUpdate.USERS_SHARED), block_user_shared),
            ],
            BlockUserConvStates.PROMPT_REASON: [
                start_menu_handler,
                main_menu_query_handler,
                CallbackQueryHandler(block_user_reason_handler(True), BLOCK_USER_SKIP_RSN.callback_data),
                MessageHandler(disable_edited_msg(filters.TEXT), block_user_reason_handler(False)),
            ],
            BlockUserConvStates.RESULT: [
                start_menu_handler,
                main_menu_query_handler,
            ]
        },
        fallbacks=[],
        map_to_parent={
            ConversationStates.MAIN_MENU: ConversationStates.MAIN_MENU,
        }
    )

    # TODO: conversation_timeout and allow_reentry ?
    invite_user_conv = ConversationHandler(
        name="invite_user_conversation",
        persistent=True,
        entry_points=[
            CallbackQueryHandler(invite_user_start, INVITE_USER.callback_data),
        ],
        states={
            InviteUserConvStates.PROMPT_USER: [
                start_menu_handler,
                main_menu_query_handler,
                MessageHandler(disable_edited_msg(filters.StatusUpdate.USERS_SHARED), invite_users_shared),
            ],
            InviteUserConvStates.PROMPT_CREDENTIALS: [
                start_menu_handler,
                main_menu_query_handler,
                MessageHandler(disable_edited_msg(filters.Regex("^\d+ \S+$")), invite_account_credentials),
            ],
            InviteUserConvStates.RESULT: [
                start_menu_handler,
                main_menu_query_handler
            ]
        },
        fallbacks=[],
        map_to_parent={
            ConversationStates.MAIN_MENU: ConversationStates.MAIN_MENU,
        }
    )

    unblock_user_conv = ConversationHandler(
        name="unblock_user_conversation",
        persistent=True,
        entry_points=[
            CallbackQueryHandler(unblock_user_start, UNBLOCK_USER.callback_data),
        ],
        states={
            UnblockUserConvStates.PROMPT_USER: [
                start_menu_handler,
                main_menu_query_handler,
                MessageHandler(disable_edited_msg(filters.StatusUpdate.USERS_SHARED), unblock_users_shared),
            ],
            UnblockUserConvStates.PROMPT_CREDENTIALS: [
                start_menu_handler,
                main_menu_query_handler,
                MessageHandler(disable_edited_msg(filters.Regex("^\d+ \S+$")), unblock_account_credentials),
            ],
            UnblockUserConvStates.RESULT: [
                start_menu_handler,
                main_menu_query_handler
            ]
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
            CallbackQueryHandler(invite_admin_start, INVITE_ADMIN.callback_data),
        ],
        states={
            InviteAdminConvStates.PROMPT_USER: [
                start_menu_handler,
                main_menu_query_handler,
                MessageHandler(disable_edited_msg(filters.StatusUpdate.USERS_SHARED), invite_admin_shared),
            ],
            InviteAdminConvStates.RESULT: [
                start_menu_handler,
                main_menu_query_handler
            ]
        },
        fallbacks=[],
        map_to_parent={
            ConversationStates.MAIN_MENU: ConversationStates.MAIN_MENU,
        }
    )

    hedge_conv = ConversationHandler(
        name="hedge_conversation",
        persistent=True,
        entry_points=[
            CallbackQueryHandler(hedge_start, HEDGE.callback_data),
        ],
        states={
            HedgeConvStates.SELECT_OPERATION: [
                start_menu_handler,
                main_menu_query_handler,
                CallbackQueryHandler(hedge_operation_selected)
            ],
            HedgeConvStates.SELECT_INSTRUMENT: [
                start_menu_handler,
                main_menu_query_handler,
                CallbackQueryHandler(hedge_instrument_selected)
            ],
            HedgeConvStates.SELECT_VOLUME: [
                start_menu_handler,
                main_menu_query_handler,
                MessageHandler(disable_edited_msg(filters.Regex("^[1-9]\d*$")), hedge_volume_selected),
            ],
            # HedgeConvStates.SELECT_CITY: [
            #     cancel_command_handler,
            #     MessageHandler(disable_edited_msg(filters.Text(cities_list)), hedge_city_selected),
            #     MessageHandler(disable_edited_msg(~filters.Text(cities_list)), hedge_invalid_city_selected)
            # ],
            HedgeConvStates.PREVIEW: [
                start_menu_handler,
                main_menu_query_handler,
                CallbackQueryHandler(hedge_preview_accepted, pattern=PLACE_ORDER.callback_data),
                CallbackQueryHandler(hedge_start, pattern=PREVIEW_EDIT.callback_data),
            ],
            HedgeConvStates.NO_ACCOUNT: [
                start_menu_handler,
                main_menu_query_handler,
            ],
            HedgeConvStates.ERR_RESULT: [
                start_menu_handler,
                main_menu_query_handler,
            ],
            HedgeConvStates.RESULT: [
                start_menu_handler,
                main_menu_query_handler
            ]
        },
        fallbacks=[],
        map_to_parent={
            ConversationStates.MAIN_MENU: ConversationStates.MAIN_MENU,
        }
    )

    conv_handler = ConversationHandler(
        name="menu_conversation",
        persistent=True,
        entry_points=[CommandHandler(["start"], start_command)],
        states = {
            ConversationStates.MAIN_MENU: [
                balance_conv,
                invite_user_conv,
                invite_admin_conv,
                block_user_conv,
                unblock_user_conv,
                change_creds_conv,
                hedge_conv,
                start_menu_handler,
                main_menu_query_handler,
            ],
        },
        fallbacks=[],
    )

    application.add_handler(conv_handler)

    # application.add_handler(balance_conv)
    # application.add_handler(hedge_conv)
    #
    # application.add_handler(CommandHandler("balance", balance_command, filters=disable_edited_msg(user_filter)))
    # application.add_handler(MessageHandler(disable_edited_msg(filters.Text([BALANCE.label]) & user_filter), balance_command))
    #
    # application.add_handler(CommandHandler("hedge", hedge_start, filters=disable_edited_msg(user_filter)),)
    # application.add_handler(MessageHandler(disable_edited_msg(filters.Text([HEDGE.label]) & user_filter), hedge_start))

    # application.add_handler(CommandHandler("help", help_command_admin, filters=disable_edited_msg(admin_filter)))
    application.add_handler(CommandHandler("help", help_command_user, filters=disable_edited_msg(user_filter)))
    application.add_handler(CommandHandler("help", help_command_unauthorized, filters=disable_edited_msg()))

    # These handlers must be added last
    application.add_handler(MessageHandler(BlockedUserFilter(), user_has_been_blocked))
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
