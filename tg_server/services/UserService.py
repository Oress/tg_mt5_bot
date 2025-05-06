from datetime import datetime

from sqlalchemy import select
from typing import List

from tg_server.constants.UserRole import UserRole
from tg_server.database.models import TgUser, Order
from tg_server.mt5.mt5service import mt5service
from tg_server.security.encryptor import pwd_encryptor


class UserService:
    def __init__(self, session):
        self.session = session

    def create_user(self, user_id: int, role: UserRole, invited_by: int, account_number: int, account_pwd_enc: bytes) -> TgUser:
        datetime_now = datetime.now()
        if not self.contains_user(user_id):
            pwd_decrypted_bytes = pwd_encryptor.decrypt(account_pwd_enc)
            account_type = mt5service.determine_account_type(account_number, pwd_decrypted_bytes.decode())

            new_user = TgUser(tg_id=user_id, invited_by=invited_by, _role=role, disabled=False,
                              mt5_acc_number=account_number, mt5_acc_pwd_enc=account_pwd_enc, account_type=account_type,
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
            pwd_decrypted_bytes = pwd_encryptor.decrypt(encrypted_pwd)
            account_type = mt5service.determine_account_type(account_number, pwd_decrypted_bytes.decode())

            user.mt5_acc_number = account_number
            user.mt5_acc_pwd_enc = encrypted_pwd
            user.account_type = account_type
        else:
            raise ValueError(f"User with ID {shared_user_id} not found.")

    def block_user(self, user_id, block_reason):
        user = self.get_user_by_id(user_id)
        if user:
            user.disabled = True
            # Watch the max length
            user.disabled_reason = block_reason
            user.mt5_acc_number = None
            user.mt5_acc_pwd_enc = None
        else:
            raise ValueError(f"User with ID {user_id} not found.")

    def unblock_user(self, user_id: int, account_number: int, account_pwd_enc: bytes):
        user = self.get_user_by_id(user_id)
        if user:
            user.disabled = False
            user.disabled_reason = None

            self.change_user_credentials(user_id, account_number, account_pwd_enc)
        else:
            raise ValueError(f"User with ID {user_id} not found.")

    def get_all_users_wo_sheet(self) -> List[TgUser]:
        stmt = select(TgUser).where(TgUser.disabled == False).where(TgUser.ggl_sheet_id == None)
        users = self.session.execute(stmt).all()
        return [x.TgUser for x in users]

    def get_all_users(self) -> List[TgUser]:
        stmt = select(TgUser).where(TgUser.disabled == False)
        users = self.session.execute(stmt).all()
        return [x.TgUser for x in users]

    def get_users_with_empty_account_types(self) -> List[TgUser]:
        stmt = select(TgUser).where(TgUser.disabled == False).where(TgUser.account_type == None)
        users = self.session.execute(stmt).all()
        return [x.TgUser for x in users]
