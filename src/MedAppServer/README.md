# MedApp Server

REST API сервер для мобильного приложения-органайзера лекарств и синхронизации общих аптечек.

## Технологии

- Kotlin 2.2.21
- Spring Boot 4.0.2
- Spring Data JPA
- Spring Security (JWT)
- PostgreSQL 15
- Docker & Docker Compose

## Архитектура

Сервер реализован по слоистой архитектуре: контроллеры → сервисы → репозитории. Подробное описание — в [ARCHITECTURE.md](../../ARCHITECTURE.md).

Проект следует принципу **Privacy by Design**:
- Сервер НЕ хранит персональные данные пользователей
- Идентификация через автоматически генерируемые ключи безопасности
- Логи выводятся только в консоль (не хранятся)

## Модель данных (кратко)

- **MedKit**: идентификатор, связи с пользователями и препаратами.
- **Drug**: название, количество, единицы измерения, форма, категория, производитель, страна, описание.
- **Using**: планируемый объем приема по препарату (резерв количества).
- **VidalDrug**: справочник препаратов для поиска по названию.

## Аутентификация и регистрация

1. `POST /auth/register` — регистрация с передачей `secret` (см. `registration.secret`).
2. `GET /auth/login` — выдача JWT по HTTP Basic.

Регистрация ограничивается по IP (кэш успешных регистраций), а JWT по умолчанию живет 10 минут.

## Запуск с Docker Compose

```bash
# Запуск всех сервисов
docker-compose up -d

# Просмотр логов
docker-compose logs -f medapp-server

# Остановка
docker-compose down

# Остановка с удалением данных
docker-compose down -v
```

## Запуск локально

### Требования
- JDK 21
- PostgreSQL 15+

### Шаги

1. Запустите PostgreSQL:
```bash
docker-compose up -d postgres
```

2. Запустите приложение:
```bash
./gradlew bootRun --args='--spring.profiles.active=dev'
```

## API Документация

После запуска доступна Swagger UI по адресу:
```
http://localhost:8080/swagger-ui/index.html
```

OpenAPI JSON:
```
http://localhost:8080/v3/api-docs
```

## Основные эндпоинты

### Аутентификация
- `POST /auth/register` - Регистрация нового пользователя
- `GET /auth/login` - Получение JWT токена

### Пользователь
- `GET /user` - Получить все данные пользователя

### Аптечки
- `POST /med-kit` - Создать аптечку
- `GET /med-kit/{id}` - Получить аптечку
- `GET /med-kit` - Получить все аптечки
- `POST /med-kit/{id}/share` - Сгенерировать ключ доступа
- `POST /med-kit/join` - Присоединиться к аптечке по ключу
- `DELETE /med-kit/{id}/leave` - Выйти из аптечки
- `DELETE /med-kit/{id}` - Удалить аптечку

### Препараты
- `POST /drug` - Создать препарат
- `GET /drug/{id}` - Получить препарат
- `PUT /drug/{id}` - Обновить препарат
- `DELETE /drug/{id}` - Удалить препарат
- `GET /drug/template/search` - Поиск в базе препаратов

### Планы лечения
- `POST /using` - Создать план лечения
- `GET /using` - Получить все планы
- `PUT /using/drug/{drugId}` - Обновить план
- `POST /using/drug/{drugId}/intake` - Отметить прием
- `DELETE /using/drug/{drugId}` - Удалить план

## Конфигурация

Основные параметры в `application.properties`:

```properties
# Срок действия JWT токена (в минутах)
authentication.term=10

# Срок действия ключа на присоединение к аптечке (в минутах)
medkit.share.termInMinutes=15

# Секрет для регистрации
registration.secret=your-secret-here

# База данных
spring.datasource.url=jdbc:postgresql://localhost:5432/medapp-server-db
```

## Тестирование

```bash
# Запуск всех тестов
./gradlew test

# Запуск с отчетом о покрытии
./gradlew test jacocoTestReport
```

## Безопасность

- JWT-based аутентификация
- RSA ключи для подписи токенов
- Stateless сессии
- Валидация всех входных данных
- Защита от SQL injection через JPA

## Логирование

Логи выводятся только в консоль в соответствии с Privacy by Design:
- `DEBUG` уровень для пакета приложения
- `INFO` для Spring компонентов
- Логи НЕ сохраняются на диск
- Логи НЕ содержат чувствительных данных

## Мониторинг

Spring Boot Actuator эндпоинты:
- `/actuator/health` - Статус здоровья
- `/actuator/info` - Информация о приложении
