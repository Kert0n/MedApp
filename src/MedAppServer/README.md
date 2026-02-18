# MedApp Server

REST API сервер для мобильного приложения-органайзера лекарств.

## Технологии

- Kotlin 2.2.21
- Spring Boot 4.0.2
- Spring Data JPA
- Spring Security (JWT)
- PostgreSQL 15
- Docker & Docker Compose

## Архитектура

Проект следует принципу **Privacy by Design**:
- Сервер НЕ хранит персональные данные пользователей
- Идентификация через автоматически генерируемые ключи безопасности
- Логи выводятся только в консоль (не хранятся)

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
- `DELETE /med-kit/{id}` - Удалить аптечку

### Препараты
- `POST /drug` - Создать препарат
- `GET /drug/{id}` - Получить препарат
- `PUT /drug/{id}` - Обновить препарат
- `DELETE /drug/{id}` - Удалить препарат
- `GET /drug/template/search` - Поиск в базе препаратов

### Планы лечения
- `POST /treatment-plan` - Создать план лечения
- `GET /treatment-plan` - Получить все планы
- `PUT /treatment-plan/drug/{drugId}` - Обновить план
- `POST /treatment-plan/drug/{drugId}/intake` - Отметить прием
- `DELETE /treatment-plan/drug/{drugId}` - Удалить план

## Конфигурация

Основные параметры в `application.properties`:

```properties
# Срок действия JWT токена (в минутах)
authentication.term=10

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
