import logging
import os
from datetime import datetime, timezone
from typing import List

from google.oauth2.service_account import Credentials
from googleapiclient.discovery import build
from sqlalchemy.orm.session import Session
from telegram.ext import ContextTypes

from tg_server.database.db import engine
from tg_server.database.models import TgUser, Order, OrderType
from tg_server.services.UserService import UserService
from sqlalchemy import select

logger = logging.getLogger(__name__)

# Define the scope for the Sheets API
SCOPES = ["https://www.googleapis.com/auth/spreadsheets", "https://www.googleapis.com/auth/drive", "https://www.googleapis.com/auth/drive.file"]
SCHEMA_VERSION = 1
METADATA_SHEET_NAME = 'METADATA'
USERS_SHEET_NAME = 'USERS'
METADATA_SHEET_ID = '0'

MD_KEY_SHEET_LAST_SYNC_DT = 'last_sheet_sync_dt'

spreadsheet_id = os.getenv("GGL_SPREADSHEET_ID")
sa_file_location = os.getenv("GGL_SA_FILE_LOCATION")
share_emails_str = os.getenv("GGL_SHARE_EMAILS") # emails separated by ;
share_emails = share_emails_str.split(";") if share_emails_str else []

credentials = Credentials.from_service_account_file(sa_file_location, scopes=SCOPES)
service = build("sheets", "v4", credentials=credentials)
drive_service = build("drive", "v3", credentials=credentials)

epoch = datetime(1899, 12, 30)


def date_to_serial(date):
    """Convert a date to Google Sheets serial number."""
    delta = date - epoch
    return delta.days + delta.seconds / 86400.0


table_definition = [
    {"label": "ДАТА", "type": "COLUMN_TYPE_UNSPECIFIED"},
    {"label": "СУММА", "type": "DOUBLE"},
    {"label": "ФИКС", "type": "DOUBLE"},
    {"label": "USDT", "type": "COLUMN_TYPE_UNSPECIFIED"},
    {"label": "Ком 0,10%", "type": "COLUMN_TYPE_UNSPECIFIED"},
    {"label": "В ПОЗИЦИИ", "type": "DOUBLE"},
    {"label": "СТАВКА", "type": "DOUBLE"},
    {"label": "ДЕПОЗИТ", "type": "COLUMN_TYPE_UNSPECIFIED"},
    {"label": "ДЕПОЗИТ ВСЕГО", "type": "COLUMN_TYPE_UNSPECIFIED"},
    {"label": "Сумма оплаты депозита", "type": "DOUBLE"},
    {"label": "БАЛАНС", "type": "COLUMN_TYPE_UNSPECIFIED"},
]

column_properties = [
    {
        "columnIndex": i,
        "columnName": col["label"],
        "columnType": col["type"]
    }
    for i, col in enumerate(table_definition)
]


def rel_cell(x, y):
    y_str = "{:+d}".format(y) if y != 0 else ""
    x_str = "{:+d}".format(x) if x != 0 else ""
    return f"INDIRECT(ADDRESS(ROW(){y_str},COLUMN(){x_str}))"


# Since the data in table is sorted by date (desc) the first row is always the last one.
def if_first_row(t_stm, f_stm):
    return f"IF({rel_cell(0, 1)}=\"\",{t_stm},{f_stm})"

async def create_metadata_sheet_if_not_exist(spreadsheet):
    sheet_titles = [sheet["properties"]["title"] for sheet in spreadsheet["sheets"]]

    if METADATA_SHEET_NAME in sheet_titles:
        logger.info("[Google Sheets][create metadata sheet] already exists")
        return

    logger.info("[Google Sheets][create metadata sheet] proceeding to create")
    service.spreadsheets().batchUpdate(
        spreadsheetId=spreadsheet['spreadsheetId'],
        body={
            "requests": [
                {
                    "addSheet": {
                        "properties": {
                            "title": METADATA_SHEET_NAME,
                            "hidden": False,
                            "gridProperties": {
                                "rowCount": 1000,
                                "columnCount": 26
                            }
                        }
                    }
                }
            ]
        }
    ).execute()


# Username and first name, last name
def get_sheet_title_for_user(user: TgUser) -> str:
    if user.username:
        return user.username
    elif user.first_name and user.last_name:
        return f"{user.first_name} {user.last_name} {user.tg_id}"
    else:
        return str(user.tg_id)

async def create_sheets_for_users_if_not_exist(spreadsheet, user: TgUser):
    logger.info(f"[Google Sheets][create user sheet] start")

    if not user :
        logger.info(f"[Google Sheets][create user sheet] No users to create sheets for")
        return

    logger.info(f"[Google Sheets][create user sheet] creating sheet for {user.tg_id}, sheetId: {user.sheet_id}")
    sheet_ids = [sheet["properties"]["sheetId"] for sheet in spreadsheet["sheets"]]

    if user.sheet_id in sheet_ids:
        logger.info(f"[Google Sheets][create user sheet] already exists sheet {user.tg_id}, sheetId: {user.sheet_id}")
        return

    result = service.spreadsheets().batchUpdate(
        spreadsheetId=user.ggl_sheet_id,
        body={
            "requests": [{
                "addSheet": {
                    "properties": {
                        "title": get_sheet_title_for_user(user),
                        "sheetId": user.sheet_id,
                        "gridProperties": {
                            "rowCount": 1000,
                            "columnCount": 26
                        }
                    }
                }
            }]
        }
    ).execute()

# Note at A1 cell
# for each user create a note with their username, first name, last name
async def update_username_notes_in_sheets(user: TgUser):
    logger.info(f"[Google Sheets][update user sheet note] start")
    if not user:
        logger.info(f"[Google Sheets][update user sheet note] nothing to update")
        return

    logger.info(f"[Google Sheets][update user sheet note] updating note for {user.tg_id}, sheetId: {user.sheet_id}")

    first_and_last = f"{user.first_name or '-'} {user.last_name or '-'}"
    username_note = (
        f"Username: {user.username or '-'}\n"
        f"Имя: {first_and_last}\n"
    )
    add_note_res = service.spreadsheets().batchUpdate(
        spreadsheetId=user.ggl_sheet_id,
        body={
            "requests": [{
                "updateCells": {
                    "rows": [
                        {
                            "values": [
                                {
                                    "note": username_note
                                }
                            ]
                        }
                    ],
                    "fields": "note",
                    "start": {
                        "sheetId": user.sheet_id,
                        "rowIndex": 0,
                        "columnIndex": 0
                    }
                }
            }]
        }
    ).execute()

currency_format = {
    "type": "NUMBER",
    "pattern": "#,##0.00"
}

text_format = {
    "fontSize": 12
}

text_format_bold = {
    **text_format,
    "bold": True
}


def create_row_data(order: Order) -> dict:
    order_dt_utc_str = order.created_at.astimezone(timezone.utc).strftime('%Y-%m-%d %H:%M:%S')
    eur_amnt = -order.euro_amount if order.type == OrderType.short else order.euro_amount
    return {
        "values": [
            {
                # date
                "userEnteredValue": {
                    "stringValue": order_dt_utc_str
                },
                "userEnteredFormat": {
                    # "numberFormat": {
                    #     "type": "DATE_TIME",
                    #     "pattern": "dd.mm.yyyy hh:mm:ss"
                    # },
                    "textFormat": text_format
                }
            },
            {
                # Summ
                "userEnteredValue": {
                    "numberValue": eur_amnt,
                },
                "userEnteredFormat": {
                    "textFormat": text_format
                }
            },
            {
                # Fix
                "userEnteredValue": {
                    "numberValue": order.price,
                },
                "userEnteredFormat": {
                    "textFormat": text_format
                }
            },
            {
                # USDT
                "userEnteredValue": {
                    "formulaValue": "=IF(B:B<>0,B:B*C:C,\"\")"
                },
                "userEnteredFormat": {
                    "numberFormat": currency_format,
                    "textFormat": text_format
                }
            },
            {
                # Fee
                "userEnteredValue": {
                    "formulaValue": "=IF(D:D<0,D:D*-0.1%,\"\")"
                },
                "userEnteredFormat": {
                    "numberFormat": currency_format,
                    "textFormat": text_format
                }
            },
            {
                # In position
                "userEnteredValue": {
                    "formulaValue": f"=B:B+{if_first_row(0, rel_cell(0, 1))}"
                },
                "userEnteredFormat": {
                    "textFormat": text_format_bold
                }
            },
            {
                # Rate
                "userEnteredValue": {
                    "formulaValue": f"={if_first_row('C:C', f'IF(B:B<0,({rel_cell(-1, 1)}*{rel_cell(0, 1)}+B:B*C:C)/F:F,{rel_cell(0, 1)})')}"
                },
                "userEnteredFormat": {
                    "textFormat": text_format
                }
            },
            {
                # Deposit
                "userEnteredValue": {
                    "formulaValue": "=IF(B:B>0,-B:B*(C:C-G:G),-E:E)"
                },
                "userEnteredFormat": {
                    "numberFormat": currency_format,
                    "textFormat": text_format
                }
            },
            {
                # Deposit total
                "userEnteredValue": {
                    "formulaValue": f"={if_first_row('H:H', f'{rel_cell(0, 1)}+H:H+{rel_cell(+1, 1)}')}"
                },
                "userEnteredFormat": {
                    "numberFormat": currency_format,
                    "textFormat": text_format_bold
                }
            },
            {
                # Payment deposit amount
                "userEnteredValue": {
                    "stringValue": ""
                },
                "userEnteredFormat": {
                    "numberFormat": currency_format,
                    "textFormat": text_format
                }
            },
            {
                # Balance
                "userEnteredValue": {
                    "formulaValue": "=I:I+J:J"
                },
                "userEnteredFormat": {
                    "numberFormat": currency_format,
                    "textFormat": text_format_bold
                }
            }
        ]
    }


async def sync_data_for_user(session: Session, user: TgUser, sync_dt: datetime):
    last_sync_dt = epoch
    logger.info(f"[Google Sheets][data sync] starting syncing data for {user.tg_id} sheetId {user.sheet_id}")
    create_metadata = True
    metadata_filters = [
        {
            "developerMetadataLookup": {
                "metadataKey": MD_KEY_SHEET_LAST_SYNC_DT,
                "metadataLocation": {
                    "sheetId": user.sheet_id,
                }
            }
        }
    ]

    request_body = {
        "dataFilters": metadata_filters
    }

    dev_metadata_resp = service.spreadsheets().developerMetadata().search(spreadsheetId=user.ggl_sheet_id,
                                                                          body=request_body).execute()

    if dev_metadata_resp and "matchedDeveloperMetadata" in dev_metadata_resp:
        # set data
        timstamp = dev_metadata_resp["matchedDeveloperMetadata"][0]["developerMetadata"]["metadataValue"]
        # formatted_time = time.strftime('%Y-%m-%d %H:%M:%S', time.gmtime(float(timstamp)))
        # dt_object = datetime.utcfromtimestamp(float(timstamp))
        # dt_object = time.gmtime(float(timstamp))
        dt_object = datetime.fromtimestamp(float(timstamp))
        logger.info(f"[Google Sheets][data sync] syncing data for {user.tg_id} sheetId {user.sheet_id}, there is a metadata {timstamp}")
        last_sync_dt = dt_object
        create_metadata = False
    else:
        logger.info(f"[Google Sheets][data sync] syncing data for {user.tg_id} sheetId {user.sheet_id}, no metadata")

    stmt = select(Order).where(Order.user_id == user.tg_id).where(Order.created_at > last_sync_dt).order_by(Order.created_at.desc())
    orders = session.execute(stmt).all()

    logger.info(f"[Google Sheets][data sync] syncing data for {user.tg_id} sheetId {user.sheet_id}, {len(orders)} orders to sync")

    if orders:
        create_dev_metadata_req = {
            "createDeveloperMetadata": {
                "developerMetadata": {
                    "visibility": "DOCUMENT",
                    "metadataId": user.sheet_id,
                    "metadataKey": MD_KEY_SHEET_LAST_SYNC_DT,
                    "metadataValue": str(sync_dt.timestamp()),
                    "location": {
                        "sheetId": user.sheet_id,
                    }
                }
            }
        }

        update_dev_metadata_req = {
            "updateDeveloperMetadata": {
                "dataFilters": metadata_filters,
                "developerMetadata": {
                    "visibility": "DOCUMENT",
                    "metadataId": user.sheet_id,
                    "metadataKey": MD_KEY_SHEET_LAST_SYNC_DT,
                    "metadataValue": str(sync_dt.timestamp()),
                    "location": {
                        "sheetId": user.sheet_id,
                    }
                },
                "fields": "*"
            }
        }

        insert_range_request = {
            "insertRange": {
                "range": {
                    "sheetId": user.sheet_id,
                    "startRowIndex": 1,  # Below headers, or use 0 to include headers
                    "endRowIndex": 1 + len(orders)
                },
                "shiftDimension": "ROWS"
            }
        }

        rows = [create_row_data(order.Order) for order in orders]

        update_cells_request = {
            "updateCells": {
                "start": {
                    "sheetId": user.sheet_id,
                    "rowIndex": 1,  # A2 = rowIndex 1
                    "columnIndex": 0  # Column A
                },
                "rows": rows,
                "fields": "*"
            }
        }

        service.spreadsheets().batchUpdate(
            spreadsheetId=user.ggl_sheet_id,
            body={
                "requests":
                    [
                        insert_range_request,
                        update_cells_request,
                        create_dev_metadata_req if create_metadata else update_dev_metadata_req
                    ]
            }
        ).execute()


def get_table_id_for_user(user: TgUser) -> str:
    return f"{user.tg_id}"


def get_table_name_for_user(user: TgUser) -> str:
    return f"HEDGE_{user.tg_id}"

def get_spreadsheet_name_for_user(user: TgUser) -> str:
    return get_sheet_title_for_user(user)


async def create_tables_for_users_if_not_exist(user_spreadsheet, user: TgUser):
    logger.info(f"[Google Sheets][create user table] start ")

    if not user:
        logger.info(f"[Google Sheets][create user table] no users to create tables for {user.tg_id} sheetId {user.sheet_id}")
        return

    # Check weather there is a sheet with a table with the same id
    has_table = False
    expected_id = get_table_id_for_user(user)
    for sheet in user_spreadsheet["sheets"]:
        if "tables" in sheet:
            for table in sheet["tables"]:
                if table["tableId"] == expected_id:
                    has_table = True
                    break

    if has_table:
        logger.info(f"[Google Sheets][create user table] table already exist for {user.tg_id} sheetId {user.sheet_id}")
        return

    logger.info(f"[Google Sheets][create user table] creating table for {user.tg_id} sheetId {user.sheet_id}")
    result = service.spreadsheets().batchUpdate(
        spreadsheetId=user.ggl_sheet_id,
        body={
            "requests": [{
                "addTable": {
                    "table": {
                        "name": get_table_name_for_user(user),
                        "tableId": get_table_id_for_user(user),
                        "range": {
                            "sheetId": user.sheet_id,
                            "startColumnIndex": 0,
                            "endColumnIndex": len(column_properties),
                            "startRowIndex": 0,
                        },
                        "columnProperties": column_properties,
                    }
                }
            }]
        }
    ).execute()
    logger.info(f"[Google Sheets][create user table] finished")


async def update_metadata_success(sync_date):
    logger.info(f"[Google Sheets][create success metadata] start")
    sync_date_utc_str = sync_date.astimezone(timezone.utc).strftime('%Y-%m-%d %H:%M:%S UTC')

    metadata = [
        ["Last Sync Time", sync_date_utc_str],
        ["Sync Status", "Success"],
        ["Schema Version", SCHEMA_VERSION]
    ]

    service.spreadsheets().values().update(
        spreadsheetId=spreadsheet_id,
        range=f"{METADATA_SHEET_NAME}!A1",
        valueInputOption="RAW",
        body={"values": metadata}
    ).execute()
    logger.info(f"[Google Sheets][create success metadata] end")

def update_metadata_exception(sync_date, e: Exception):
    logger.error(f"[Google Sheets][create error metadata] start")

    sync_date_utc_str = sync_date.astimezone(timezone.utc).strftime('%Y-%m-%d %H:%M:%S UTC')

    metadata = [
        ["Last Sync Time", sync_date_utc_str],
        ["Sync Status", f"Error: {str(e)}"],
        ["Schema Version", SCHEMA_VERSION]
    ]

    service.spreadsheets().values().update(
        spreadsheetId=spreadsheet_id,
        range=f"{METADATA_SHEET_NAME}!A1",
        valueInputOption="RAW",
        body={"values": metadata}
    ).execute()
    logger.error(f"[Google Sheets][create error metadata] end")

async def remove_0_sheet_if_exist(spreadsheet):
    logger.info(f"[Google Sheets][remove 0 sheet] start")
    sheet_ids = [sheet["properties"]["sheetId"] for sheet in spreadsheet["sheets"]]

    if 0 not in sheet_ids:
        logger.info(f"[Google Sheets][remove 0 sheet] no sheet")
        return

    service.spreadsheets().batchUpdate(
        spreadsheetId=spreadsheet['spreadsheetId'],
        body={
            "requests": [
                {
                    "deleteSheet": {
                        "sheetId": 0
                    }
                }
            ]
        }
    ).execute()
    logger.info(f"[Google Sheets][remove 0 sheet] end")

async def create_spreadsheets(users: List[TgUser]):
    logger.info(f"[Google Sheets][create spreadsheet] start")
    for user in users:
        logger.info(f"[Google Sheets][create spreadsheet] creating spreadsheet for {user.tg_id}")

        spreadsheet_body = {
            "properties": {
                "title": get_spreadsheet_name_for_user(user)
            }
        }

        spreadsheet = service.spreadsheets().create(
            body=spreadsheet_body,
            fields="spreadsheetId"
        ).execute()

        spreadsheet_id = spreadsheet['spreadsheetId']

        logger.info(f"[Google Sheets][create spreadsheet] Created spreadsheet for user {user.tg_id} spreadsheet: {spreadsheet}")

        for email in share_emails:
            logger.info(f"[Google Sheets][create spreadsheet] sharing spreadsheet with {email}")
            drive_service.permissions().create(
                fileId=spreadsheet_id,
                body={
                    'type': 'user',
                    'role': 'writer',
                    'emailAddress': email
                },
                sendNotificationEmail=True
            ).execute()

        user.ggl_sheet_id = spreadsheet_id

        logger.info(f"[Google Sheets][create spreadsheet] end creating table for {user.tg_id}")
    logger.info(f"[Google Sheets][create spreadsheet] end")

async def add_users_to_main_spreadsheet(spreadsheet, users: List[TgUser]):
    logger.info(f"[Google Sheets][create success metadata] start")

    range_name = f'{USERS_SHEET_NAME}!A1'  # Replace with your sheet name and range

    values = []
    for user in users:
        sheet_id = user.ggl_sheet_id
        sheet_url = f"https://docs.google.com/spreadsheets/d/{sheet_id}"
        values.append([user.tg_id, user.username, user.first_name, sheet_url])

    body = {
        'values': values
    }

    result = service.spreadsheets().values().append(
        spreadsheetId=spreadsheet['spreadsheetId'],
        range=range_name,
        valueInputOption='RAW',
        insertDataOption='INSERT_ROWS',
        body=body
    ).execute()
    logger.info(f"[Google Sheets][create success metadata] end")

async def create_users_sheet_if_not_exist(spreadsheet):
    sheet_titles = [sheet["properties"]["title"] for sheet in spreadsheet["sheets"]]

    if USERS_SHEET_NAME in sheet_titles:
        logger.info("[Google Sheets][create users sheet] already exists")
        return

    logger.info("[Google Sheets][create users sheet] proceeding to create")
    service.spreadsheets().batchUpdate(
        spreadsheetId=spreadsheet['spreadsheetId'],
        body={
            "requests": [
                {
                    "addSheet": {
                        "properties": {
                            "title": USERS_SHEET_NAME,
                            "hidden": False,
                            "gridProperties": {
                                "rowCount": 1000,
                                "columnCount": 26
                            }
                        }
                    }
                }
            ]
        }
    ).execute()


async def sync_with_google_sheets(context: ContextTypes.DEFAULT_TYPE):
    logger.info(f"[Google Sheets][sync] start")

    sync_date = datetime.now()
    try:
        common_spreadsheet = service.spreadsheets().get(spreadsheetId=spreadsheet_id, includeGridData=False,
                                                 excludeTablesInBandedRanges=True).execute()

        # Prepare common spreadsheet. Create users and metadata tab, remove 0 tab if exist
        await create_users_sheet_if_not_exist(common_spreadsheet)
        await create_metadata_sheet_if_not_exist(common_spreadsheet)
        await remove_0_sheet_if_exist(common_spreadsheet)

        users_to_create_ids = []
        with Session(engine) as session:
            user_service = UserService(session)
            users_to_create = user_service.get_all_users_wo_sheet()
            users_to_create_ids = [user.tg_id for user in users_to_create]
            await create_spreadsheets(users_to_create)
            session.commit()

        with Session(engine) as session:
            user_service = UserService(session)
            users = user_service.get_all_users()
            users_to_create = [user for user in users if user.tg_id in users_to_create_ids]
            await add_users_to_main_spreadsheet(common_spreadsheet, users_to_create)

            for user in users:
                user_spreadsheet = (service.spreadsheets()
                                    .get(spreadsheetId=user.ggl_sheet_id,
                                         includeGridData=False,
                                         excludeTablesInBandedRanges=True)
                                    .execute())

                await create_sheets_for_users_if_not_exist(user_spreadsheet, user)
                await remove_0_sheet_if_exist(user_spreadsheet)

                await update_username_notes_in_sheets(user)
                await create_tables_for_users_if_not_exist(user_spreadsheet, user)
                await sync_data_for_user(session, user, sync_date)

        await update_metadata_success(sync_date)
    except Exception as e:
        update_metadata_exception(sync_date, e)
        logger.error(f"[Google Sheets][sync] failed error: {e}", exc_info=e)
    logger.info(f"[Google Sheets][sync] end")

    return
