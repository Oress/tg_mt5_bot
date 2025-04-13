FROM python:3.8-bullseye AS base

# Set environment variables
ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1

# Set working directory
WORKDIR /tg_server

# Install system dependencies
RUN apt-get update && apt-get install -y --no-install-recommends \
    build-essential \
    curl \
    && rm -rf /var/lib/apt/lists/*

FROM base AS builder

# Install pip tools and create virtual environment
RUN python -m venv /venv
ENV PATH="/venv/bin:$PATH"

# Copy only dependency files to cache efficiently
COPY requirements.txt ./

# Install Python dependencies
RUN pip install --upgrade pip \
    && pip install --no-cache-dir -r requirements.txt

FROM base AS final

# Copy virtual environment from builder
COPY --from=builder /venv /venv
ENV PATH="/venv/bin:$PATH"

# Copy application code
COPY . .

# Port (optional, for web apps or Django)
# EXPOSE 8000

# Entry point (edit as needed)
CMD ["python", "main.py"]