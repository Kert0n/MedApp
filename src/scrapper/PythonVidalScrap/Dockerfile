# Vidal.ru Scraper Docker Image
FROM python:3.11-slim

LABEL maintainer="vidal-scraper"
LABEL description="Scraper for vidal.ru pharmaceutical database"

# Установка зависимостей системы
RUN apt-get update && apt-get install -y --no-install-recommends \
    postgresql-client \
    curl \
    && rm -rf /var/lib/apt/lists/*

# Рабочая директория
WORKDIR /app

# Копируем requirements и устанавливаем Python зависимости
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Копируем код
COPY scraper.py .
COPY form_types.py .
COPY entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

# Создаём директорию для данных
RUN mkdir -p /data/csv_backup

# Healthcheck
HEALTHCHECK --interval=30s --timeout=10s --start-period=5s --retries=3 \
    CMD curl -f http://localhost:8080/health || exit 1

# По умолчанию запускаем скраппер
ENTRYPOINT ["/entrypoint.sh"]
CMD ["scrape"]
