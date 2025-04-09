from datetime import datetime, timedelta
from uuid import uuid4

from sqlalchemy import text
from telegram import helpers

from tg_server.constants.UserRole import UserRole
from tg_server.database.db import engine


class InviteLinkService:
    def __init__(self, engine):
        """
        Initialize the service with a database connection or ORM session.
        :param engine: Database connection or ORM session.
        """
        self.engine = engine

    def create_invite_link(self, bot_username: str, created_by: int, created_for: int, role_on_accept: UserRole, expires_in_minutes: int = 30) -> str:
        """
        Create a new invite link.
        :param bot_username: username of the bot for a deep link.
        :param created_by: ID of the user creating the invite link.
        :param created_for: ID of the invite link is created for.
        :param role_on_accept: Role to assign when the invite is accepted.
        :param expires_in_minutes: Expiration time in minutes.
        :return: The generated invite link code.
        """
        code = str(uuid4())

        with self.engine.connect() as conn:
            datetime_now = datetime.now()
            expires_at = datetime.utcnow() + timedelta(hours=expires_in_minutes)
            now = datetime_now.strftime('%Y-%m-%d %H:%M:%S')
            exp = expires_at.strftime('%Y-%m-%d %H:%M:%S')

            conn.execute(
                text(
                    "insert into tgb_invite_links (code, tg_userid_for, role_on_accept, created_by, created_at, expires_at)"
                    " values (:code, :userid_for, :role, :created_by, :created_at, :exp_at)"),
                [{"code": code, "userid_for": created_for, "role": role_on_accept, "created_by": created_by,
                  "created_at": now, "exp_at": exp}],
            )
            conn.commit()

        return helpers.create_deep_linked_url(bot_username, code)

    # TODO: check expiration
    def validate_invite_link(self, user_id: int, code: str) -> bool:
        """
        Validate an invite link.
        :param code: The invite link code.
        :return: Invite link details if valid, None otherwise.
        """
        with self.engine.connect() as conn:
            result = conn.execute(
                text("select 1 from tgb_invite_links where tg_userid_for = :user_id and code = :code"),
                [{"user_id": user_id, "code": code }],
            )
            valid_invite_link = result.one_or_none()
            print(valid_invite_link)
            return valid_invite_link is not None

invite_link_service = InviteLinkService(engine)