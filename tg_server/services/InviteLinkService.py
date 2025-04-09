from datetime import datetime, timedelta
from typing import Union
from uuid import uuid4
from sqlalchemy import text, select
from telegram import helpers

from tg_server.constants.UserRole import UserRole
from tg_server.database.models import InviteLink


class InviteLinkService:
    def __init__(self, session):
        self.session = session

    def create_invite_link(self, bot_username: str, created_by: int, created_for: int, role_on_accept: UserRole,
                           expires_in_minutes: int = 30) -> Union[str, None]:
        code = str(uuid4())

        if self.__contains_invite_link(created_for):
            return None

        datetime_now = datetime.utcnow()
        expires_at = datetime_now + timedelta(hours=expires_in_minutes)

        new_link = InviteLink(code=code, tg_userid_for=created_for, created_by=created_by,
                              role_on_accept=role_on_accept, created_at=datetime_now, expires_at=expires_at)
        self.session.add(new_link)

        return helpers.create_deep_linked_url(bot_username, code)

    # TODO: check expiration
    def get_invite_link(self, user_id: int, code: str) -> InviteLink:
        stmt = (select(InviteLink)
                .where(InviteLink.tg_userid_for == user_id)
                .where(InviteLink.code == code)
                )
        valid_invite_link = self.session.execute(stmt).first()
        return valid_invite_link

    def __contains_invite_link(self, user_id: int) -> bool:
        stmt = select(InviteLink).where(InviteLink.tg_userid_for == user_id)
        valid_invite_link = self.session.execute(stmt).first()
        return valid_invite_link is not None
