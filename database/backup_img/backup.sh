#!/bin/bash

echo "Backup started"
echo $(/usr/bin/pg_dump -w -h "$POSTGRES_DB_HOST" -U "$POSTGRES_USER" 2> /error.log) > /db_backup.sql
cat /error.log
echo "Backup finished. Uploading to B2..."
/b2 file upload $B2_BUCKET_NAME /db_backup.sql $(date +"%Y-%m-%d-%H-%M-%S.backup") 2> /error1.log
cat /error1.log
echo "Upload finished."
