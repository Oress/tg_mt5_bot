from datetime import datetime, timedelta
from typing import Union
from uuid import uuid4
from sqlalchemy import text, select

from tg_server.constants.UserRole import UserRole
from tg_server.database.models import InviteLink


class InviteLinkService:
    def __init__(self, session):
        self.session = session

    def create_invite_link(self, created_by: int, created_for: int, role_on_accept: UserRole,
                           account_number: int = None, account_pwd_enc: bytes = None,
                           expires_in_minutes: int = 30) -> Union[str, None]:
        code = str(uuid4())

        if self.contains_invite_link(created_for):
            return None

        datetime_now = datetime.utcnow()
        expires_at = datetime_now + timedelta(minutes=expires_in_minutes)

        new_link = InviteLink(code=code, tg_userid_for=created_for, _created_by=created_by,
                              mt5_acc_number=account_number, mt5_acc_pwd_enc=account_pwd_enc,
                              _role_on_accept=role_on_accept, created_at=datetime_now, expires_at=expires_at)
        self.session.add(new_link)

        return code

    # TODO: check expiration
    def get_invite_link(self, user_id: int, code: str) -> InviteLink:
        stmt = (select(InviteLink)
                .where(InviteLink.tg_userid_for == user_id)
                .where(InviteLink.code == code)
                )
        valid_invite_link = self.session.execute(stmt).first()
        return valid_invite_link.InviteLink if valid_invite_link else None

    def get_link_by_account_num(self, account_num: int) -> InviteLink:
        stmt = select(InviteLink).where(InviteLink.mt5_acc_number == account_num)
        user = self.session.execute(stmt).first()
        return user.InviteLink if user else None

    def contains_invite_link(self, user_id: int) -> bool:
        return self.get_invite_link_by_id(user_id) is not None

    def get_invite_link_by_id(self, user_id: int) -> InviteLink:
        stmt = select(InviteLink).where(InviteLink.tg_userid_for == user_id)
        valid_invite_link = self.session.execute(stmt).first()
        return valid_invite_link.InviteLink if valid_invite_link else None
