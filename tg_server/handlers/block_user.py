import logging

from ptbcontrib.extract_passed_user import extract_passed_user
from sqlalchemy.orm.session import Session
from telegram import helpers, Update, ReplyKeyboardRemove, ReplyKeyboardMarkup, KeyboardButton
from telegram.constants import ParseMode
from telegram.ext import ContextTypes

from tg_server.database.db import engine
from tg_server.handlers.commons import send_main_menu, main_menu_decorator
from tg_server.handlers.invite import choose_user_markup
from tg_server.handlers.states import ConversationStates, BlockUserConvStates
from tg_server.services.UserService import UserService

logger = logging.getLogger(__name__)

UD_KEY_USERID_TO_BLOCK = "UD_KEY_USERID_TO_BLOCK"

BTN_SKIP = "Пропустить"

async def block_user_start(update: Update, context: ContextTypes.DEFAULT_TYPE):
    logger.warning(f"[Block User Start], chat_id: {update.effective_chat.id}, username: {update.effective_user.username}, user_id {update.effective_user.id}")
    await update.message.reply_text(
        helpers.escape_markdown("Пожалуйста, выберете пользователя, используя меню", 2),
        reply_markup=choose_user_markup,
        parse_mode=ParseMode.MARKDOWN_V2,
    )

    return BlockUserConvStates.PROMPT_USER


async def block_user_shared_invalid(update: Update, context: ContextTypes.DEFAULT_TYPE):
    logger.warning(f"[Block User] invalid user, chat_id: {update.effective_chat.id}, username: {update.effective_user.username}, user_id {update.effective_user.id}, message: {update.message.text}")
    return await block_user_start(update, context)

async def block_user_shared(update: Update, context: ContextTypes.DEFAULT_TYPE):
    logger.warning(f"[Block User] user shared, chat_id: {update.effective_chat.id}, username: {update.effective_user.username}, user_id {update.effective_user.id}, message: {update.message.text}")
    shared_user = await extract_passed_user(message=update.message)

    if shared_user is None:
        return await block_user_start(update, context)

    with Session(engine) as session:
        user_service = UserService(session)
        user = user_service.get_user_by_id(shared_user.user_id)
        if not user:
            await update.message.reply_text(
                helpers.escape_markdown("Этот пользователь не зарегистрирован. Выберите другого пользователя.", 2),
                reply_markup=choose_user_markup,
                parse_mode=ParseMode.MARKDOWN_V2,
            )
            return BlockUserConvStates.PROMPT_USER

        elif user.disabled:
            reason = user.disabled_reason if user.disabled_reason else "не указана"
            await update.message.reply_text(
                helpers.escape_markdown(f"Этот пользователь уже заблокирован.\nПричина: {reason} \n Выберите другого пользователя.", 2),
                reply_markup=choose_user_markup,
                parse_mode=ParseMode.MARKDOWN_V2,
            )
            return BlockUserConvStates.PROMPT_USER

        context.user_data[UD_KEY_USERID_TO_BLOCK] = shared_user.user_id

        await update.message.reply_text(
            helpers.escape_markdown(
                f"(Опционально) Укажите причину", 2),
            parse_mode=ParseMode.MARKDOWN_V2,
            reply_markup=ReplyKeyboardMarkup(main_menu_decorator([[BTN_SKIP]]),
            resize_keyboard=True))

    return BlockUserConvStates.PROMPT_REASON


async def block_user_reason_entered(update: Update, context: ContextTypes.DEFAULT_TYPE):
    logger.info(f"[Block User] Invite creds entered, chat_id: {update.effective_chat.id}, username: {update.effective_user.username}, user_id {update.effective_user.id}")

    with Session(engine) as session:
        user_service = UserService(session)
        shared_user_id: int = context.user_data.get(UD_KEY_USERID_TO_BLOCK)
        block_reason = None if update.message.text == BTN_SKIP else update.message.text
        user_service.block_user(shared_user_id, block_reason)
        session.commit()

    await update.message.reply_text(
        helpers.escape_markdown(
            f"Пользователь был заблокирован.", 2),
        parse_mode=ParseMode.MARKDOWN_V2,
        reply_markup=ReplyKeyboardRemove(),
    )

    await send_main_menu(update, context)
    return ConversationStates.MAIN_MENU


async def block_user_cancel(update: Update, context: ContextTypes.DEFAULT_TYPE):
    """Cancels and ends the conversation."""
    user = update.message.from_user
    logger.info("[Block User] User %s canceled the conversation.", user.first_name)
    await update.message.reply_text(helpers.escape_markdown("Отмена генерации приглашения.", 2),
                                    reply_markup=ReplyKeyboardRemove(), parse_mode=ParseMode.MARKDOWN_V2)
    await send_main_menu(update, context)
    return ConversationStates.MAIN_MENU


