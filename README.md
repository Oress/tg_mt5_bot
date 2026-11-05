 Deployment Documentation

The new opt-in WebSocket worker and automatic EA startup are documented in [mt5_gateway/README.md](mt5_gateway/README.md). Native x86 MT5 validation is still pending; existing deployment files continue to use the HTTP proxy.

## Table of Contents
1. [Introduction](#introduction)
2. [Prerequisites](#prerequisites)
3. [Setup](#setup)
4. [Configuration](#configuration)
5. [Deployment Steps](#deployment-steps)
6. [Post-Deployment](#post-deployment)
7. [Troubleshooting](#troubleshooting)
8. [References](#references)

---

## Introduction
This is a bot that interacts with a trading platform MT5 (Gerchik):
- It works only with NET accounts, hedging accounts were not tested.
- Sending buy/sell market orders.
- Managing multiple trading accounts.
- Basic administration commands (invite user, invite admin, block user, unblock user, change user's credentials).
- User commands (get balance, hedge).
- Exports trading history to google sheet.
---

## Prerequisites
The deployment was tested on the following environment:
- **Operating System**: Ubuntu 22.04
- **Env. Dependencies**: Docker
---

## Setup
Explain how to prepare the environment:
1. Clone the repository:
   ```bash
   git clone https://github.com/Oress/tg_mt5_bot.git
   cd tg_mt5_bot
2. Install docker using convenience script (https://docs.docker.com/engine/install/ubuntu/#install-using-the-convenience-script:
3. Create .env file (copy from dev.env):
4. For the export to Google Sheets, you need to create a project in the Google Developer Console and enable the Google Sheets API and Google Drive API. Follow these steps:
   - Go to the Google Developer Console.
   - Create a new project.
   - Enable the Google Sheets API and Google Drive API for your project.
   - Create credentials for a service account and download the JSON file.
   - Create a Google Sheet where you want to export the data.
   - Share the Google Sheet with the service account email address (found in the JSON file).
   - Copy the JSON file to the root of the project as ./keys/key.json
5. For the database backup, you need to create a Backblaze account create a bucket and generate a write only application key for it. Follow these steps:
   - Go to the Backblaze B2 Cloud Storage.
   - Create a new application key (Not master key).
   - Copy the application key and secret key.
   - Add lifecycle rules to the bucket to delete files older than 30 days (or 15).
   
## Configuration
Python server env. variables:
APP_VERSION - Not used actually
LOGS_FOLDER - Logs folder
POSTGRES_DB_HOST - Database host
POSTGRES_DB_PORT - Database port
POSTGRES_DB - Database name
POSTGRES_USER - Database user
POSTGRES_PASSWORD - Database password
MT5_HOST - Host of the MT5 server
MT5_PORT - Port of the MT5 server
MT5_SERVER - 

ENCRYPTION_KEY
HEDGE_CITIES
SUPERADMIN_ID=
TG_BOT_TOKEN=

ALLOY_USERNAME=
GRAFANA_TOKEN=

CUSTOM_USER=
PASSWORD=

B2_BUCKET_NAME
B2_APPLICATION_KEY_ID
B2_APPLICATION_KEY
BACKUP_SCHEDULE

GGL_SPREADSHEET_ID
GGL_SHEET_SYNC_INVTERVAL
