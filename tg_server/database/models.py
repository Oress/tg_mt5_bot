from sqlalchemy import MetaData
from sqlalchemy import Table, Column, Integer, String, BigInteger, TIMESTAMP

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
