FROM postgres:16.0

LABEL maintainer="oress"

# Update package lists and upgrade packages
RUN apt-get update && apt-get upgrade -y

# install cron and wget
RUN apt-get install -y cron wget

# Install backblaze b2 command line tool and chmod it
RUN wget -O b2 https://github.com/Backblaze/B2_Command_Line_Tool/releases/latest/download/b2-linux \
    && chmod +x ./b2

#ENV B2_APPLICATION_KEY_ID="key_id"
#ENV B2_APPLICATION_KEY="key"
#ENV B2_BUCKET_NAME="bckt_name"
#ENV BACKUP_SCHEDULE="@daily"
ENV PGPASSFILE=/.pgpass

COPY backup.sh backup.sh
COPY init.sh init.sh

RUN chmod +x backup.sh init.sh

ENTRYPOINT ["/init.sh"]
