import os

from sqlalchemy import create_engine

db_host=os.getenv('POSTGRES_DB_HOST')
db_port=os.getenv('POSTGRES_DB_PORT')
db_name=os.getenv('POSTGRES_DB')
db_user=os.getenv('POSTGRES_USER')
db_pwd=os.getenv('POSTGRES_PASSWORD')

engine = create_engine(f"postgresql+psycopg://{db_user}:{db_user}@{db_host}:{db_port}/{db_name}", echo=True)