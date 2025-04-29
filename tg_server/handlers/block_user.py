import logging

from ptbcontrib.extract_passed_user import extract_passed_user
from sqlalchemy.orm.session import Session
from telegram import helpers, Update, InlineKeyboardButton, InlineKeyboardMarkup
from telegram.constants import ParseMode
from telegram.ext import ContextTypes

from tg_server.database.db import engine
from tg_server.handlers.commons import main_menu_decorator, UD_KEY_MENU_MSG_ID, BLOCK_USER_SKIP_RSN, \
    add_message_to_delete, purge_messages
from tg_server.handlers.invite import choose_user_markup
from tg_server.handlers.states import BlockUserConvStates
from tg_server.services.UserService import UserService

logger = logging.getLogger(__name__)

UD_KEY_USERID_TO_BLOCK = "UD_KEY_USERID_TO_BLOCK"

async def block_user_start(update: Update, context: ContextTypes.DEFAULT_TYPE):
    logger.warning(f"[Block User][start][chat_id: {update.effective_chat.id}][username: {update.effective_user.username}][user_id: {update.effective_user.id}]")

    query = update.callback_query
    await query.answer()

    await query.edit_message_text(
        text=helpers.escape_markdown("Пожалуйста, выберете пользователя, используя меню", 2),
        reply_markup=InlineKeyboardMarkup(main_menu_decorator([])),
        parse_mode=ParseMode.MARKDOWN_V2
    )

    prompt_msg = await update.effective_user.send_message(
        text="Выберете пользователя:",
        reply_markup=choose_user_markup,
        parse_mode=ParseMode.MARKDOWN_V2,
    )
    add_message_to_delete([prompt_msg], context)

    return BlockUserConvStates.PROMPT_USER


# async def block_user_shared_invalid(update: Update, context: ContextTypes.DEFAULT_TYPE):
#     logger.warning(f"[Block User] invalid user, chat_id: {update.effective_chat.id}, username: {update.effective_user.username}, user_id {update.effective_user.id}")
#     return await block_user_start(update, context)

async def block_user_shared(update: Update, context: ContextTypes.DEFAULT_TYPE):
    logger.info(f"[Block User][user shared][chat_id: {update.effective_chat.id}][username: {update.effective_user.username}][user_id: {update.effective_user.id}]")
    shared_user = await extract_passed_user(message=update.message)

    add_message_to_delete([update.message], context)
    await purge_messages(update, context)
    view_msg_id = context.user_data.get(UD_KEY_MENU_MSG_ID)

    with Session(engine) as session:
        user_service = UserService(session)
        user = user_service.get_user_by_id(shared_user.user_id)
        if not user:
            # May throw the Message is not modified error.
            try:
                await context.bot.edit_message_text(
                    chat_id=update.effective_chat.id,
                    message_id=view_msg_id,
                    text=helpers.escape_markdown("Этот пользователь не зарегистрирован. Выберите другого пользователя.", 2),
                    reply_markup=InlineKeyboardMarkup(main_menu_decorator([])),
                    parse_mode=ParseMode.MARKDOWN_V2
                )
            except Exception as e:
                logger.error(f"[Block User][not registered][chat_id: {update.effective_chat.id}][username: {update.effective_user.username}][user_id: {update.effective_user.id}][exception: {e}]")

            prompt_msg = await update.effective_user.send_message(
                text="Выберете пользователя:",
                reply_markup=choose_user_markup,
                parse_mode=ParseMode.MARKDOWN_V2,
            )
            add_message_to_delete([prompt_msg], context)
            return BlockUserConvStates.PROMPT_USER

        elif user.disabled:
            reason = user.disabled_reason if user.disabled_reason else "не указана"
            try:
                await context.bot.edit_message_text(
                    chat_id=update.effective_chat.id,
                    message_id=view_msg_id,
                    text=helpers.escape_markdown(f"Этот пользователь уже заблокирован.\nПричина: {reason} \nВыберите другого пользователя.", 2),
                    reply_markup=InlineKeyboardMarkup(main_menu_decorator([])),
                    parse_mode=ParseMode.MARKDOWN_V2
                )
            except Exception as e:
                logger.error(f"[Block User][user blocked][chat_id: {update.effective_chat.id}][username: {update.effective_user.username}][user_id: {update.effective_user.id}][exception: {e}]")

            prompt_msg = await update.effective_user.send_message(
                text="Выберете пользователя:",
                reply_markup=choose_user_markup,
                parse_mode=ParseMode.MARKDOWN_V2,
            )
            add_message_to_delete([prompt_msg], context)
            return BlockUserConvStates.PROMPT_USER

        context.user_data[UD_KEY_USERID_TO_BLOCK] = shared_user.user_id


        await context.bot.edit_message_text(
            chat_id=update.effective_chat.id,
            message_id=view_msg_id,
            text=helpers.escape_markdown(f"(Опционально) Укажите причину", 2),
            reply_markup=InlineKeyboardMarkup(main_menu_decorator([[
                InlineKeyboardButton(text=BLOCK_USER_SKIP_RSN.label, callback_data=BLOCK_USER_SKIP_RSN.callback_data),
            ]])),
            parse_mode=ParseMode.MARKDOWN_V2
        )

    return BlockUserConvStates.PROMPT_REASON

def block_user_reason_handler(skip: bool):
    async def block_user_reason_entered(update: Update, context: ContextTypes.DEFAULT_TYPE):
        logger.info(f"[Block User][reason entered][chat_id: {update.effective_chat.id}][username: {update.effective_user.username}][user_id: {update.effective_user.id}][skip: {skip}]")

        if skip:
            query = update.callback_query
            await query.answer()
        else:
            add_message_to_delete([update.message], context)

        with Session(engine) as session:
            user_service = UserService(session)
            shared_user_id: int = context.user_data.get(UD_KEY_USERID_TO_BLOCK)
            block_reason = None if skip else update.message.text
            user_service.block_user(shared_user_id, block_reason)
            session.commit()

        view_msg_id = context.user_data.get(UD_KEY_MENU_MSG_ID)
        await context.bot.edit_message_text(
            chat_id=update.effective_chat.id,
            message_id=view_msg_id,
            text=helpers.escape_markdown(f"Пользователь был заблокирован.", 2),
            parse_mode=ParseMode.MARKDOWN_V2,
            reply_markup=InlineKeyboardMarkup(main_menu_decorator([])),
        )

        return BlockUserConvStates.RESULT

    return block_user_reason_entered

# async def block_user_cancel(update: Update, context: ContextTypes.DEFAULT_TYPE):
#     """Cancels and ends the conversation."""
#     user = update.message.from_user
#     logger.info("[Block User] User %s canceled the conversation.", user.first_name)
#     await update.message.reply_text(helpers.escape_markdown("Отмена генерации приглашения.", 2),
#                                     reply_markup=ReplyKeyboardRemove(), parse_mode=ParseMode.MARKDOWN_V2)
#     await send_main_menu(update, context)
#     return ConversationStates.MAIN_MENU


