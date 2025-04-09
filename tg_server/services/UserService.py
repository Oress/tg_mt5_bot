from datetime import datetime

from sqlalchemy import text

class UserService:
    def __init__(self, conn):
        self.conn = conn

    def register_user(self, user_id, invited_by):
        datetime_now = datetime.utcnow()

        if not self.contains_user(user_id):
            self.conn.execute(
                text("insert into tgb_users (tg_id, invited_by, disabled, disabled_reason, created_at, updated_at) values (:user_id, :invited_by, false, null, :created_at, :updated_at)"),
                [{"user_id": user_id, "invited_by": invited_by, "created_at": datetime_now, "updated_at": datetime_now}],
            )

    def contains_user(self, user_id: int) -> bool:
        """
        :param user_id: The id of telegram user.
        :return: True if the user exists, False otherwise.
        """
        result = self.conn.execute(
            text("select 1 from tgb_users where tg_id = :user_id"),
            [{"user_id": user_id }],
        )
        registered_user = result.one_or_none()
        return registered_user is not None
