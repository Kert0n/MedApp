# Vidal.ru Scraper v5.2

Парсер справочника лекарств vidal.ru с полным перечнем форм Минздрава РФ.

## 🚀 Быстрый старт

### Docker (рекомендуется)

```bash
chmod +x vidal.sh entrypoint.sh

./vidal.sh start   # Запуск
./vidal.sh logs    # Логи
./vidal.sh status  # Статус
```

### Локальный запуск (без Docker)

```bash
pip install -r requirements.txt

# Запуск с SQLite (по умолчанию)
python3 scraper.py --data-dir ./data

# С PostgreSQL
export DATABASE_URL="postgresql://user:pass@localhost:5432/vidal"
python3 scraper.py --data-dir ./data
```

## ⚙️ Параметры командной строки

```bash
python3 scraper.py --help

# Основные параметры:
  --data-dir PATH      # Директория для данных (по умолчанию: ./data)
  --db-url URL         # URL базы данных (по умолчанию: SQLite в data-dir)
  --limit N            # Ограничить количество препаратов
  --stats              # Показать статистику
  --reset              # Сбросить прогресс
  --test               # Тест извлечения форм
  --test-live          # Тест на реальных данных
  --healthcheck        # Запустить HTTP сервер для healthcheck
  --healthcheck-port N # Порт healthcheck сервера (по умолчанию: 8080)
```

## 🏥 Healthcheck и мониторинг

Скрипт поддерживает HTTP healthcheck endpoint:

```bash
# Запуск с healthcheck сервером
python3 scraper.py --healthcheck --healthcheck-port 8080

# Проверка статуса
curl http://localhost:8080/health
```

Ответ:
```json
{
  "status": "running",
  "db_connected": true,
  "last_scrape": "2024-01-10T12:00:00",
  "scraped_count": 1500,
  "error_count": 5
}
```

## 📋 Перечень лекарственных форм

Используется **официальный перечень Минздрава РФ** (350+ форм):

- Аэрозоли (8 видов)
- Гели (20 видов)
- Гранулы (10 видов)
- Капли (10 видов)
- Капсулы (11 видов)
- Концентраты
- Кремы (8 видов)
- Лиофилизаты (11 видов)
- Мази (9 видов)
- Пластыри
- Порошки (17 видов)
- Растворы (47 видов!)
- Спреи (7 видов)
- Суппозитории
- Суспензии (20 видов)
- Таблетки (23 вида)
- Эмульсии (14 видов)
- И другие...

## 📦 Структура данных

```
drugs:
  drug_id           - ID из vidal
  name              - Название
  name_lat          - Латинское
  form              - Форма полностью
  form_type         - Тип по Минздраву (капли глазные, таблетки покрытые пленочной оболочкой, ...)
  quantity          - Количество в упаковке
  dosage            - Дозировка
  active_substance  - Действующее вещество
  category          - Категория (КФГ)
  description       - Описание
  manufacturer      - Производитель
  country           - Страна
  otc               - Без рецепта (true/false)
  url               - Источник
```

## 📤 Экспорт данных

**CSV** (создаётся параллельно с БД):
```bash
./data/drugs.csv
```

**PostgreSQL**:
```bash
./vidal.sh psql           # CLI
./vidal.sh export-dump    # SQL дамп
```

## 🔄 Миграция данных в PostgreSQL

### Из SQLite в PostgreSQL

```bash
# 1. Создать дамп из SQLite
sqlite3 ./data/vidal_drugs.db ".dump drugs" > drugs_dump.sql

# 2. Преобразовать для PostgreSQL
sed -i 's/INTEGER PRIMARY KEY AUTOINCREMENT/SERIAL PRIMARY KEY/g' drugs_dump.sql
sed -i 's/DATETIME/TIMESTAMP/g' drugs_dump.sql
sed -i 's/\"//g' drugs_dump.sql

# 3. Импортировать в PostgreSQL
psql -U vidal -d vidal -f drugs_dump.sql
```

### Из CSV в PostgreSQL

```bash
# 1. Создать таблицу (если не существует)
psql -U vidal -d vidal -c "
CREATE TABLE IF NOT EXISTS drugs (
    id SERIAL PRIMARY KEY,
    drug_id VARCHAR(30) UNIQUE NOT NULL,
    name VARCHAR(300) NOT NULL,
    name_lat VARCHAR(300),
    form VARCHAR(500),
    form_type VARCHAR(100),
    quantity INTEGER DEFAULT 0,
    dosage VARCHAR(100),
    active_substance VARCHAR(300),
    category VARCHAR(300),
    description TEXT,
    manufacturer VARCHAR(300),
    country VARCHAR(100),
    otc BOOLEAN,
    url VARCHAR(300),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_drugs_drug_id ON drugs(drug_id);
CREATE INDEX IF NOT EXISTS idx_drugs_name ON drugs(name);
CREATE INDEX IF NOT EXISTS idx_drugs_form_type ON drugs(form_type);
CREATE INDEX IF NOT EXISTS idx_drugs_manufacturer ON drugs(manufacturer);
CREATE INDEX IF NOT EXISTS idx_drugs_active_substance ON drugs(active_substance);
"

# 2. Импортировать CSV
psql -U vidal -d vidal -c "\copy drugs(drug_id, name, name_lat, form, form_type, quantity, dosage, active_substance, category, description, manufacturer, country, otc, url) FROM './data/drugs.csv' WITH CSV HEADER"
```

### Полная миграция с Docker

```bash
# 1. Создать дамп
./vidal.sh export-dump

# 2. Скопировать на новый сервер
scp vidal_*.sql.gz user@newserver:/path/

# 3. На новом сервере
gunzip vidal_*.sql.gz
psql -U vidal -d vidal -f vidal_*.sql
```

## 🧪 Тесты

```bash
# Тест извлечения форм (offline)
python3 scraper.py --test

# Тест на реальных данных (требует доступ к vidal.ru)
python3 scraper.py --test-live
```

## 🛠 Docker команды

| Команда | Описание |
|---------|----------|
| `./vidal.sh start` | Запуск |
| `./vidal.sh stop` | Остановка |
| `./vidal.sh logs` | Логи |
| `./vidal.sh status` | Статус |
| `./vidal.sh stats` | Статистика |
| `./vidal.sh psql` | PostgreSQL CLI |
| `./vidal.sh export-dump` | SQL дамп |
| `./vidal.sh reset` | Сброс всех данных |

## 🔧 Переменные окружения

| Переменная | Описание | По умолчанию |
|------------|----------|--------------|
| `DATABASE_URL` | URL PostgreSQL | SQLite fallback |
| `DATA_DIR` | Директория данных | `./data` |
| `MIN_DELAY` | Минимальная задержка (сек) | `3.0` |
| `MAX_DELAY` | Максимальная задержка (сек) | `7.0` |
| `MAX_RETRIES` | Максимум попыток | `5` |
| `HTTP_TIMEOUT` | Таймаут HTTP запросов (сек) | `30` |
| `CONNECT_TIMEOUT` | Таймаут подключения (сек) | `10` |

## ⏱ Время выполнения

~25,000 препаратов × 5 сек ≈ **35 часов**

Прогресс сохраняется каждые 10 записей в `progress.json`.

## 📁 Структура файлов

```
data/
├── drugs.csv           # Данные в CSV
├── progress.json       # Прогресс парсинга
└── vidal_drugs.db      # SQLite база (если не используется PostgreSQL)
```
