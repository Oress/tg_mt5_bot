from datetime import datetime

from sqlalchemy import select

from tg_server.constants.UserRole import UserRole
from tg_server.database.models import TgUser

class UserService:
    def __init__(self, session):
        self.session = session

    def create_user(self, user_id: int, role: UserRole, invited_by: int, account_number: int, account_pwd_enc: bytes) -> TgUser:
        datetime_now = datetime.utcnow()
        if not self.contains_user(user_id):
            new_user = TgUser(tg_id=user_id, invited_by=invited_by, _role=role, disabled=False,
                              mt5_acc_number=account_number, mt5_acc_pwd_enc=account_pwd_enc,
                              disabled_reason=None, created_at=datetime_now, updated_at=datetime_now)
            self.session.add(new_user)
            return new_user

    def get_user_by_id(self, user_id: int) -> TgUser:
        stmt = select(TgUser).where(TgUser.tg_id == user_id)
        registered_user = self.session.execute(stmt).first()
        return registered_user.TgUser if registered_user else None

    def get_user_by_account_num(self, account_num: int) -> TgUser:
        stmt = select(TgUser).where(TgUser.mt5_acc_number == account_num)
        user = self.session.execute(stmt).first()
        return user.TgUser if user else None

    def contains_user(self, user_id: int) -> bool:
        return self.get_user_by_id(user_id) is not None

    def change_user_credentials(self, shared_user_id, account_number, encrypted_pwd):
        user = self.get_user_by_id(shared_user_id)
        if user:
            user.mt5_acc_number = account_number
            user.mt5_acc_pwd_enc = encrypted_pwd
        else:
            raise ValueError(f"User with ID {shared_user_id} not found.")
