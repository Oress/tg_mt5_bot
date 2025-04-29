import logging

from ptbcontrib.extract_passed_user import extract_passed_user
from sqlalchemy.orm.session import Session
from telegram import helpers, Update, InlineKeyboardMarkup
from telegram.constants import ParseMode
from telegram.ext import ContextTypes

from tg_server.constants.UserRole import UserRole
from tg_server.database.db import engine
from tg_server.handlers.commons import add_message_to_delete, main_menu_decorator, purge_messages, \
    UD_KEY_MENU_MSG_ID
from tg_server.handlers.invite import choose_user_markup
from tg_server.handlers.states import InviteUserConvStates, InviteAdminConvStates
from tg_server.services.InviteLinkService import InviteLinkService
from tg_server.services.UserService import UserService

logger = logging.getLogger(__name__)

UD_KEY_ROLE_ON_ACCEPT = "role_on_accept"
UD_KEY_USER_ID_TO_INVITE = "user_id_to_invite"

ADMIN_INVITE_PREFIX_MD2 = "*\[Приглашение \(админ\)\]*\n"


async def invite_admin_start(update: Update, context: ContextTypes.DEFAULT_TYPE):
    logger.warning(f"[Invite Admin Start], chat_id: {update.effective_chat.id}, username: {update.effective_user.username}, user_id {update.effective_user.id}")

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

    return InviteUserConvStates.PROMPT_USER


# async def invite_admin_shared_invalid(update: Update, context: ContextTypes.DEFAULT_TYPE):
#     logger.warning(f"[Invite Admin invalid user], chat_id: {update.effective_chat.id}, username: {update.effective_user.username}, user_id {update.effective_user.id}")
#     return await invite_admin_start(update, context)

async def invite_admin_shared(update: Update, context: ContextTypes.DEFAULT_TYPE):
    logger.warning(f"[Invite Admin user shared], chat_id: {update.effective_chat.id}, username: {update.effective_user.username}, user_id {update.effective_user.id}")
    shared_user = await extract_passed_user(message=update.message)

    add_message_to_delete([update.message], context)
    await purge_messages(update, context)
    view_msg_id = context.user_data.get(UD_KEY_MENU_MSG_ID)


    with Session(engine) as session:
        invite_link_service = InviteLinkService(session)
        link = invite_link_service.get_invite_link_by_id(shared_user.user_id)
        if link is not None:
            # May return Message is not modified: specified new message content and reply markup are exactly the same as a current content and reply markup of the message
            try:
                await context.bot.edit_message_text(
                    chat_id=update.effective_chat.id,
                    message_id=view_msg_id,
                    text=helpers.escape_markdown("У этого пользователя уже есть ожидающие приглашение (см. ниже). Выберите другого пользователя.", 2),
                    reply_markup=InlineKeyboardMarkup(main_menu_decorator([])),
                    parse_mode=ParseMode.MARKDOWN_V2
                )
            except Exception as e:
                logger.error(f"[Invite user] Error editing message: {e}")

            invite_msg = await show_admin_invite(update, context, link.code)
            add_message_to_delete([invite_msg], context)

            prompt_msg = await update.effective_user.send_message(
                text="Выберете пользователя:",
                reply_markup=choose_user_markup,
                parse_mode=ParseMode.MARKDOWN_V2,
            )
            add_message_to_delete([prompt_msg], context)
            return InviteAdminConvStates.PROMPT_USER

        user_service = UserService(session)
        contains_user = user_service.contains_user(shared_user.user_id)
        if contains_user:
            try:
                await context.bot.edit_message_text(
                    chat_id=update.effective_chat.id,
                    message_id=view_msg_id,
                    text=helpers.escape_markdown("Этот пользователь уже зарегистрирован. Выберите другого пользователя.", 2),
                    reply_markup=InlineKeyboardMarkup(main_menu_decorator([])),
                    parse_mode=ParseMode.MARKDOWN_V2
                )
            except Exception as e:
                logger.error(f"[Invite user] Error editing message: {e}")

            prompt_msg = await update.effective_user.send_message(
                text="Выберете пользователя:",
                reply_markup=choose_user_markup,
                parse_mode=ParseMode.MARKDOWN_V2,
            )
            # context.user_data[UD_KEY_INVT_USR_PROMPT_MSG_ID] = prompt_msg.message_id
            add_message_to_delete([prompt_msg], context)
            return InviteAdminConvStates.PROMPT_USER

        await context.bot.edit_message_text(
            chat_id=update.effective_chat.id,
            message_id=view_msg_id,
            text= helpers.escape_markdown(f"Перешлите следующее сообщение пользователю.\nГенерация приглашения...", 2),
            reply_markup=InlineKeyboardMarkup(main_menu_decorator([])),
            parse_mode=ParseMode.MARKDOWN_V2
        )
        invite_code = invite_link_service.create_invite_link(update.effective_user.id, shared_user.user_id, UserRole.USER)
        session.commit()

    invite_msg = await show_admin_invite(update, context, invite_code)
    add_message_to_delete([invite_msg], context)
    # await send_main_menu(update, context)
    return InviteAdminConvStates.RESULT

async def show_admin_invite(update, context, invite_code):
    deep_link = helpers.create_deep_linked_url(context.bot.username, invite_code)
    invite_link = f"[Нажмите здесь, чтобы присоединиться]({deep_link})"
    return await update.message.reply_text(
        f"{ADMIN_INVITE_PREFIX_MD2} {invite_link}",
        parse_mode=ParseMode.MARKDOWN_V2,
    )

# async def invite_admin_cancel(update: Update, context: ContextTypes.DEFAULT_TYPE):
#     """Cancels and ends the conversation."""
#     user = update.message.from_user
#     logger.info("User %s canceled the conversation.", user.first_name)
#     await update.message.reply_text(helpers.escape_markdown("Отмена генерации приглашения.", 2),
#                                     reply_markup=ReplyKeyboardRemove(), parse_mode=ParseMode.MARKDOWN_V2)
#     await send_main_menu(update, context)
#     return ConversationStates.MAIN_MENU


