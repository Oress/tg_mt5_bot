from sqlalchemy import MetaData, LargeBinary, Boolean, String, BigInteger, TIMESTAMP
from sqlalchemy.orm import DeclarativeBase
from datetime import datetime

from sqlalchemy.ext.hybrid import hybrid_property

from typing import Optional
from sqlalchemy.orm import Mapped
from sqlalchemy.orm import mapped_column

metadata_obj = MetaData()

class Base(DeclarativeBase):
    pass

class InviteLink(Base):
    __tablename__ = "tgb_invite_links"
    code: Mapped[str] = mapped_column(String, primary_key=True)
    tg_userid_for: Mapped[int] = mapped_column(BigInteger)
    _created_by: Mapped[Optional[int]] = mapped_column("created_by", BigInteger)
    _role_on_accept: Mapped[str] = mapped_column("role_on_accept", String)
    mt5_acc_number: Mapped[Optional[int]] = mapped_column(BigInteger)
    mt5_acc_pwd_enc: Mapped[Optional[bytes]] = mapped_column(LargeBinary)
    created_at: Mapped[Optional[datetime]] = mapped_column(TIMESTAMP)
    expires_at: Mapped[Optional[datetime]] = mapped_column(TIMESTAMP)

    @hybrid_property
    def role_on_accept(self):
        return self._role_on_accept

    @role_on_accept.setter
    def role_on_accept(self, email):
        self._role_on_accept = email

    @hybrid_property
    def created_by(self):
        return self._created_by

    @created_by.setter
    def created_by(self, created_by):
        self._created_by = created_by

    def __repr__(self):
        return f"InviteLink(code={self.code}, tg_userid_for={self.tg_userid_for})" \

class TgUser(Base):
    __tablename__ = "tgb_users"
    tg_id: Mapped[int] = mapped_column(BigInteger, primary_key=True)
    invited_by: Mapped[int] = mapped_column(BigInteger)
    disabled: Mapped[bool] = mapped_column(Boolean)
    _role: Mapped[str] = mapped_column(name="role", type_=String)
    disabled_reason: Mapped[Optional[str]] = mapped_column(String)
    mt5_acc_number: Mapped[Optional[int]] = mapped_column(BigInteger)
    mt5_acc_pwd_enc: Mapped[Optional[bytes]] = mapped_column(LargeBinary)
    created_at: Mapped[Optional[datetime]] = mapped_column(TIMESTAMP)
    updated_at: Mapped[Optional[datetime]] = mapped_column(TIMESTAMP)

    @hybrid_property
    def role(self):
        return self._role

    @role.setter
    def role(self, role):
        self._role = role

    def __repr__(self):
        return f"TgUser(tg_id={self.tg_id}, invited_by={self.invited_by}, role={self._role})"

