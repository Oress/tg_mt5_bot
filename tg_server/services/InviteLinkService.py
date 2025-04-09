from datetime import datetime, timedelta
from typing import Union
from uuid import uuid4
from sqlalchemy import text, select
from telegram import helpers

from tg_server.constants.UserRole import UserRole
from tg_server.database.models import invite_links_table


class InviteLinkService:
    def __init__(self, conn):
        self.conn = conn

    def create_invite_link(self, bot_username: str, created_by: int, created_for: int, role_on_accept: UserRole, expires_in_minutes: int = 30) -> Union[str, None]:
        code = str(uuid4())

        if self.__contains_invite_link(created_for):
            return None

        datetime_now = datetime.utcnow()
        expires_at = datetime_now + timedelta(hours=expires_in_minutes)
        now = datetime_now.strftime('%Y-%m-%d %H:%M:%S')
        exp = expires_at.strftime('%Y-%m-%d %H:%M:%S')

        self.conn.execute(
            text(
                "insert into tgb_invite_links (code, tg_userid_for, role_on_accept, created_by, created_at, expires_at)"
                " values (:code, :userid_for, :role, :created_by, :created_at, :exp_at)"),
            [{"code": code, "userid_for": created_for, "role": role_on_accept, "created_by": created_by,
              "created_at": now, "exp_at": exp}],
        )

        return helpers.create_deep_linked_url(bot_username, code)

    # TODO: check expiration
    def get_invite_link(self, user_id: int, code: str) -> dict:

        result = self.conn.execute(
            text("select * from tgb_invite_links where tg_userid_for = :user_id and code = :code"),
            [{"user_id": user_id, "code": code }],
        )
        valid_invite_link = result.one_or_none()
        return valid_invite_link

    def __contains_invite_link(self, user_id: int) -> bool:
        result = self.conn.execute(
            text(
                "select 1 from tgb_invite_links where tg_userid_for = :user_id "),
            [{"user_id": user_id }],
        )
        valid_invite_link = result.one_or_none()
        return valid_invite_link is not None

