import logging

from ptbcontrib.extract_passed_user import extract_passed_user
from sqlalchemy.orm.session import Session
from telegram import helpers, Update, ReplyKeyboardRemove
from telegram.constants import ParseMode
from telegram.ext import ContextTypes

from tg_server.constants.UserRole import UserRole
from tg_server.database.db import engine
from tg_server.handlers.commons import send_main_menu
from tg_server.handlers.invite import share_handle_msg, choose_user_markup
from tg_server.handlers.states import InviteUserConvStates, ConversationStates, InviteAdminConvStates
from tg_server.services.InviteLinkService import InviteLinkService
from tg_server.services.UserService import UserService

logger = logging.getLogger(__name__)

UD_KEY_ROLE_ON_ACCEPT = "role_on_accept"
UD_KEY_USER_ID_TO_INVITE = "user_id_to_invite"

GENERATE_ADMIN_INVITE_PREFIX_MD2 = "*\[Generate Invite Admin\]*\n"
ADMIN_INVITE_PREFIX_MD2 = "*\[Invite\]*\n"


share_handle_msg = (GENERATE_ADMIN_INVITE_PREFIX_MD2
                    + helpers.escape_markdown("Please share the user using the menu", 2)
                    )

async def invite_admin_start(update: Update, context: ContextTypes.DEFAULT_TYPE):
    await update.message.reply_text(
        share_handle_msg + "\n\nUse /cancel command to go back to main menu\.",
        reply_markup=choose_user_markup,
        parse_mode=ParseMode.MARKDOWN_V2,
    )

    return InviteUserConvStates.PROMPT_USER


async def invite_admin_shared_invalid(update: Update, context: ContextTypes.DEFAULT_TYPE):
    return await invite_admin_start(update, context)


async def invite_admin_shared(update: Update, context: ContextTypes.DEFAULT_TYPE):
    shared_user = await extract_passed_user(message=update.message)

    if shared_user is None:
        return await invite_admin_start(update, context)

    with Session(engine) as session:
        invite_link_service = InviteLinkService(session)
        link = invite_link_service.get_invite_link_by_id(shared_user.user_id)
        if link is not None:
            await update.message.reply_text(
                GENERATE_ADMIN_INVITE_PREFIX_MD2 + helpers.escape_markdown("This user already has a pending invite link (see below). Please select another user.", 2),
                reply_markup=choose_user_markup,
                parse_mode=ParseMode.MARKDOWN_V2,
                protect_content=True
            )
            await show_admin_invite(update, context, link.code)
            return InviteAdminConvStates.PROMPT_USER

        user_service = UserService(session)
        contains_user = user_service.contains_user(shared_user.user_id)
        if contains_user:
            await update.message.reply_text(
                GENERATE_ADMIN_INVITE_PREFIX_MD2 + helpers.escape_markdown("This user is already registered. Please select another user.", 2),
                reply_markup=choose_user_markup,
                parse_mode=ParseMode.MARKDOWN_V2,
                protect_content=True
            )
            return InviteAdminConvStates.PROMPT_USER

        await update.message.reply_text(
            GENERATE_ADMIN_INVITE_PREFIX_MD2 + helpers.escape_markdown(
                f"Forward the next message to the user.\nGenerating invite link...", 2),
            parse_mode=ParseMode.MARKDOWN_V2,
            protect_content=True
        )
        invite_code = invite_link_service.create_invite_link(update.effective_user.id, shared_user.user_id, UserRole.USER)
        session.commit()

    await show_admin_invite(update, context, invite_code)
    await send_main_menu(update, context)
    return ConversationStates.MAIN_MENU

async def show_admin_invite(update, context, invite_code):
    deep_link = helpers.create_deep_linked_url(context.bot.username, invite_code)
    invite_link = f"[Click here to join]({deep_link})"
    await update.message.reply_text(
        f"{ADMIN_INVITE_PREFIX_MD2}{invite_link}",
        parse_mode=ParseMode.MARKDOWN_V2,
    )

async def invite_admin_cancel(update: Update, context: ContextTypes.DEFAULT_TYPE):
    """Cancels and ends the conversation."""
    user = update.message.from_user
    logger.info("User %s canceled the conversation.", user.first_name)
    await update.message.reply_text(GENERATE_ADMIN_INVITE_PREFIX_MD2 + helpers.escape_markdown("Canceling invite generation.", 2),
                                    reply_markup=ReplyKeyboardRemove(), parse_mode=ParseMode.MARKDOWN_V2)
    await send_main_menu(update, context)
    return ConversationStates.MAIN_MENU


