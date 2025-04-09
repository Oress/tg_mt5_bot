from sqlalchemy import MetaData, Table, Column, Boolean, String, BigInteger, TIMESTAMP
from sqlalchemy.orm import DeclarativeBase
from datetime import datetime

from sqlalchemy.ext.hybrid import hybrid_property

from typing import Optional
from sqlalchemy.orm import Mapped
from sqlalchemy.orm import mapped_column

metadata_obj = MetaData()

invite_links_table = Table(
    "tgb_invite_links",
    metadata_obj,
    Column("code", String, primary_key=True),
    Column("tg_userid_for", BigInteger),
    Column("created_by", BigInteger),
    Column("role_on_accept", String),
    Column("created_at", TIMESTAMP),
    Column("expires_at", TIMESTAMP),
)

class Base(DeclarativeBase):
    pass

class InviteLink(Base):
    __tablename__ = "tgb_invite_links"
    code: Mapped[str] = mapped_column(String, primary_key=True)
    tg_userid_for: Mapped[int] = mapped_column(BigInteger)
    created_by: Mapped[Optional[int]] = mapped_column(BigInteger)
    role_on_accept: Mapped[str] = mapped_column(String)
    created_at: Mapped[Optional[datetime]] = mapped_column(TIMESTAMP)
    expires_at: Mapped[Optional[datetime]] = mapped_column(TIMESTAMP)

    # @hybrid_property
    # def code(self):
    #     return self._code
    #
    # @code.setter
    # def code(self, email):
    #     self._code = email

    def __repr__(self):
        return f"InviteLink(code={self.code}, tg_userid_for={self.tg_userid_for})" \

class TgUser(Base):
    __tablename__ = "tgb_users"
    tg_id: Mapped[int] = mapped_column(BigInteger, primary_key=True)
    invited_by: Mapped[int] = mapped_column(BigInteger)
    disabled: Mapped[bool] = mapped_column(Boolean)
    role: Mapped[str] = mapped_column(String)
    disabled_reason: Mapped[Optional[str]] = mapped_column(String)
    created_at: Mapped[Optional[datetime]] = mapped_column(TIMESTAMP)
    updated_at: Mapped[Optional[datetime]] = mapped_column(TIMESTAMP)

