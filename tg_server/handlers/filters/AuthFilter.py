from sqlalchemy.orm.session import Session
from telegram.ext.filters import MessageFilter

from tg_server.constants.UserRole import UserRole
from tg_server.database.db import engine
from tg_server.database.models import TgUser
from tg_server.services.UserService import UserService

# TODO: think of optimization because of frequent DB calls, lru cache?
class AuthFilter(MessageFilter):
    def __init__(self, roles=[]):
        super().__init__()
        self.roles = roles

    def filter(self, message):
        # TODO: check forwarded messages
        with Session(engine) as session:
            user_service = UserService(session)

            user_id = message.from_user.id
            user = user_service.get_user_by_id(user_id)
            role = user.role if user is not None else None
            print(f"role for user {user_id}: {role}, expected roles: {self.roles}")

            return not user.disabled and role in self.roles


class BlockedUserFilter(MessageFilter):
    def __init__(self):
        super().__init__()

    def filter(self, message):
        with Session(engine) as session:
            user_service = UserService(session)

            user_id = message.from_user.id
            user = user_service.get_user_by_id(user_id)

            return user.disabled

superadmin_filter = AuthFilter([UserRole.SUPERADMIN])
admin_filter = AuthFilter([UserRole.SUPERADMIN, UserRole.ADMIN])
manager_filter = AuthFilter([UserRole.SUPERADMIN, UserRole.ADMIN,UserRole.MANAGER])
user_filter = AuthFilter([ UserRole.SUPERADMIN, UserRole.ADMIN, UserRole.MANAGER, UserRole.USER])