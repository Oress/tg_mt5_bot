from datetime import datetime

from sqlalchemy import select

from tg_server.constants.UserRole import UserRole
from tg_server.database.models import TgUser

class UserService:
    def __init__(self, session):
        self.session = session

    def register_user(self, user_id, invited_by):
        self.cretate_user(user_id, UserRole.USER, invited_by)

    def register_superadmin(self, user_id):
        self.cretate_user(user_id, UserRole.SUPERADMIN, None)

    def cretate_user(self, user_id, role, invited_by):
        datetime_now = datetime.utcnow()
        if not self.contains_user(user_id):
            new_user = TgUser(tg_id=user_id, invited_by=invited_by, role=role, disabled=False,
                              disabled_reason=None, created_at=datetime_now, updated_at=datetime_now)
            self.session.add(new_user)

    def contains_user(self, user_id: int) -> bool:
        stmt = select(TgUser).where(TgUser.tg_id == user_id)
        registered_user = self.session.execute(stmt).first()
        return registered_user is not None
