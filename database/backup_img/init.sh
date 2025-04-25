#!/bin/bash

# Set environment variables for PostgreSQL connection
# https://www.postgresql.org/docs/current/libpq-pgpass.html
echo "$POSTGRES_DB_HOST:$POSTGRES_DB_PORT:$POSTGRES_DB:$POSTGRES_USER:$POSTGRES_PASSWORD" > $PGPASSFILE \
    && chmod 0600 $PGPASSFILE;

echo "$BACKUP_SCHEDULE root B2_APPLICATION_KEY=$B2_APPLICATION_KEY B2_APPLICATION_KEY_ID=$B2_APPLICATION_KEY_ID PGPASSFILE=$PGPASSFILE B2_BUCKET_NAME=$B2_BUCKET_NAME POSTGRES_DB_HOST=$POSTGRES_DB_HOST POSTGRES_USER=$POSTGRES_USER /backup.sh >> /my-job.log" > /etc/cron.d/b2-backup \
    && chmod 0644 /etc/cron.d/b2-backup \
    && crontab /etc/cron.d/b2-backup

cron -f