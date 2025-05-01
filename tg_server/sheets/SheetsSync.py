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
SCOPES = ["https://www.googleapis.com/auth/spreadsheets"]
SCHEMA_VERSION = 1
METADATA_SHEET_NAME = 'METADATA'
METADATA_SHEET_ID = '0'

MD_KEY_SHEET_LAST_SYNC_DT = 'last_sheet_sync_dt'

spreadsheet_id = os.getenv("GGL_SPREADSHEET_ID")
sa_file_location = os.getenv("GGL_SA_FILE_LOCATION")

credentials = Credentials.from_service_account_file(sa_file_location, scopes=SCOPES)
service = build("sheets", "v4", credentials=credentials)

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
    {"label": "Дата оплаты", "type": "DATE_TIME"},
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

def create_metadata_sheet_if_not_exist(spreadsheet):
    # [{'properties': {'sheetId': 0, 'title': 'Sheet1', 'index': 0, 'sheetType': 'GRID', 'gridProperties': {'rowCount': 997, 'columnCount': 26, 'frozenRowCount': 1}}}]
    sheet_titles = [sheet["properties"]["title"] for sheet in spreadsheet["sheets"]]

    if METADATA_SHEET_NAME in sheet_titles:
        logger.info("[Google Sheets][create metadata sheet] already exists")
        return

    logger.info("[Google Sheets][create metadata sheet] proceeding to create")
    service.spreadsheets().batchUpdate(
        spreadsheetId=spreadsheet_id,
        body={
            "requests": [
                {
                    "addSheet": {
                        "properties": {
                            "title": METADATA_SHEET_NAME,
                            "hidden": True,
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
    return ", ".join([user.username or "-", user.first_name or "-", user.last_name or "-"])


# It should work with dev metadata to allow user to rename it.
def create_sheets_for_users_if_not_exist(users_to_create: List[TgUser]):
    if not users_to_create or len(users_to_create) == 0:
        logger.info(f"[Google Sheets][create user sheet] No users to create sheets for")
        return

    logger.info(f"[Google Sheets][create user sheet] proceeding to create {len(users_to_create)} sheets")

    addSheet_requests = []
    for user in users_to_create:
        logger.info(f"[Google Sheets][create user sheet] creating sheet for {user.tg_id}, sheetId: {user.sheet_id}")

        addSheet_requests.append({
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
        })

    result = service.spreadsheets().batchUpdate(
        spreadsheetId=spreadsheet_id,
        body={
            "requests": addSheet_requests
        }
    ).execute()

# Note at A1 cell
# for each user create a note with their username, first name, last name
def update_username_notes_in_sheets(users: List[TgUser]):
    if not users or len(users) == 0:
        logger.info(f"[Google Sheets][update user sheet note] nothing to update")
        return

    logger.info(f"[Google Sheets][update user sheet note] proceeding to update")

    add_notes_requests = []
    for user in users:
        logger.info(f"[Google Sheets][update user sheet note] updating note for {user.tg_id}, sheetId: {user.sheet_id}")

        first_and_last = f"{user.first_name or '-'} {user.last_name or '-'}"
        username_note = (
            f"Username: {user.username or '-'}\n"
            f"Имя: {first_and_last}\n"
        )
        add_notes_requests.append({
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
        })

    add_note_res = service.spreadsheets().batchUpdate(
        spreadsheetId=spreadsheet_id,
        body={
            "requests": add_notes_requests
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
                    "formulaValue": f"={if_first_row('H:H', f'{rel_cell(0, 1)}+H:H+{rel_cell(+2, 1)}')}"
                },
                "userEnteredFormat": {
                    "numberFormat": currency_format,
                    "textFormat": text_format_bold
                }
            },
            {
                # Payment date
                "userEnteredValue": {
                    "stringValue": ""
                },
                "userEnteredFormat": {
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
                    "formulaValue": "=I:I+K:K"
                },
                "userEnteredFormat": {
                    "numberFormat": currency_format,
                    "textFormat": text_format_bold
                }
            }
        ]
    }


def sync_data(users: List[TgUser], sync_dt: datetime):
    logger.info(f"[Google Sheets][data sync] syncing data for {len(users)} users dt: {sync_dt}")

    last_sync_dt = epoch

    with Session(engine) as session:
        for user in users:
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

            dev_metadata_resp = service.spreadsheets().developerMetadata().search(spreadsheetId=spreadsheet_id,
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
                    spreadsheetId=spreadsheet_id,
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


def create_tables_for_users_if_not_exist(users_to_create: List[TgUser]):
    if not users_to_create or len(users_to_create) == 0:
        logger.info(f"[Google Sheets][create user table] no users to create tables for")
        return

    logger.info(f"[Google Sheets][create user table] start creating tables, {len(users_to_create)} users to create")

    add_table_requests = []
    for user in users_to_create:
        logger.info(f"[Google Sheets][create user table] creating table for {user.tg_id} sheetId {user.sheet_id}")
        add_table_requests.append({
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
        })

    result = service.spreadsheets().batchUpdate(
        spreadsheetId=spreadsheet_id,
        body={
            "requests": add_table_requests
        }
    ).execute()
    logger.info(f"[Google Sheets][create user table] finished")


def update_metadata_success(sync_date):
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

def remove_0_sheet_if_exist(spreadsheet):
    logger.info(f"[Google Sheets][remove 0 sheet] start")
    # [{'properties': {'sheetId': 0, 'title': 'Sheet1', 'index': 0, 'sheetType': 'GRID', 'gridProperties': {'rowCount': 997, 'columnCount': 26, 'frozenRowCount': 1}}}]
    sheet_ids = [sheet["properties"]["sheetId"] for sheet in spreadsheet["sheets"]]

    if 0 not in sheet_ids:
        logger.info(f"[Google Sheets][remove 0 sheet] no sheet")
        return

    service.spreadsheets().batchUpdate(
        spreadsheetId=spreadsheet_id,
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

async def sync_with_google_sheets(context: ContextTypes.DEFAULT_TYPE):
    # ALGORITHM
    # Get the spreadsheet basic information
    # Create or update metadata in the spreadsheet to track basic information
    # read all non-blocked users and create separate sheet for each user if not exist
    # For each such sheet, create if not exist a table
    # Sync the table with the database records. (HOW?)
    # Create if not exist a separate sheet called Metadata for storing metadata.
    # NOTES:
    # Due to the Google Sheets limitations. The max number of tables in a sheet is 200, so this design supports only < 200 users. Otherwise, new solution is needed.
    logger.info(f"[Google Sheets][sync] start")

    sync_date = datetime.now()
    try:
        spreadsheet = service.spreadsheets().get(spreadsheetId=spreadsheet_id, includeGridData=False,
                                                 excludeTablesInBandedRanges=True).execute()
        with Session(engine) as session:
            user_service = UserService(session)
            users = user_service.get_all_non_blocked_users()

        sheet_ids = [sheet["properties"]["sheetId"] for sheet in spreadsheet["sheets"]]
        users_to_create = [user for user in users if user.sheet_id not in sheet_ids]

        create_metadata_sheet_if_not_exist(spreadsheet)

        remove_0_sheet_if_exist(spreadsheet)

        create_sheets_for_users_if_not_exist(users_to_create)

        update_username_notes_in_sheets(users)

        create_tables_for_users_if_not_exist(users_to_create)

        sync_data(users, sync_date)

        update_metadata_success(sync_date)
    except Exception as e:
        update_metadata_exception(sync_date, e)
        logger.error(f"[Google Sheets][sync] failed error: {e}", exc_info=e)
    logger.info(f"[Google Sheets][sync] end")

    return
