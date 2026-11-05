FROM ubuntu:22.04

ARG WINE_VERSION=11.0.0.0~jammy-1
ENV DEBIAN_FRONTEND=noninteractive WINEPREFIX=/opt/wine WINEARCH=win64 WINEDEBUG=-all \
    DISPLAY=:99 PYTHONUNBUFFERED=1
RUN dpkg --add-architecture i386 && apt-get update && apt-get install -y --no-install-recommends \
    ca-certificates curl gnupg python3 supervisor xvfb xauth x11-utils \
    && mkdir -p /etc/apt/keyrings \
    && curl -fsSL https://dl.winehq.org/wine-builds/winehq.key | gpg --dearmor -o /etc/apt/keyrings/winehq.gpg \
    && echo 'deb [signed-by=/etc/apt/keyrings/winehq.gpg] https://dl.winehq.org/wine-builds/ubuntu/ jammy main' > /etc/apt/sources.list.d/winehq.list \
    && apt-get update && apt-get install -y --install-recommends "winehq-stable=$WINE_VERSION" \
    && rm -rf /var/lib/apt/lists/*

ARG MT5_INSTALLER_URL=https://download.mql5.com/cdn/web/gerchik.and.co/mt5/gerchikco5setup.exe
ENV MT5_INSTALLER_URL=$MT5_INSTALLER_URL
COPY scripts/install-terminal.sh /tmp/install-terminal.sh
RUN bash /tmp/install-terminal.sh

RUN curl -fsSL --retry 3 https://www.python.org/ftp/python/3.11.9/python-3.11.9-amd64.exe -o /tmp/python.exe \
    && xvfb-run -a wine /tmp/python.exe /quiet InstallAllUsers=1 'TargetDir=C:\Python311' Include_test=0 PrependPath=1 \
    && wineserver -w && rm /tmp/python.exe
COPY requirements.txt /tmp/requirements.txt
RUN xvfb-run -a wine 'C:\Python311\python.exe' -m pip install --no-cache-dir -r Z:\\tmp\\requirements.txt

COPY . /opt/gateway
RUN chmod +x /opt/gateway/scripts/*.sh && /opt/gateway/scripts/compile-ea.sh
WORKDIR /opt/gateway
EXPOSE 8000
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s \
    CMD curl -fsS http://127.0.0.1:8000/health || exit 1
ENTRYPOINT ["/opt/gateway/scripts/entrypoint.sh"]
