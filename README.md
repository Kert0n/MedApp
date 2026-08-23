# MedApp

Курсовая работа: система учёта домашней аптечки. Аптечки бывают общими — семья, соседи. Система
приватна по замыслу: о человеке не хранится ничего, кроме идентификатора и хеша ключа.

Репозиторий зонтичный: здесь документы курсовой, а части подключены подмодулями — у каждой своя
сборка, свой CI и своя история.

## Части

| часть | что это | репозиторий |
|---|---|---|
| `src/MedAppServer` | REST API: аптечки, упаковки, брони, справочник, синхронизация | [MedApp-Server](https://github.com/Kert0n/MedApp-Server) |
| `src/AndroidApp` | клиент для Android | [MedApp-Android](https://github.com/Kert0n/MedApp-Android) |
| `src/scrapper` | скраппер справочника vidal.ru, код одноразовый | [MedApp-Scrapper](https://github.com/Kert0n/MedApp-Scrapper) |

## Клонировать

```bash
git clone --recurse-submodules https://github.com/Kert0n/MedApp.git
```

Без `--recurse-submodules` каталоги частей останутся пустыми. Уже клонировали без него:

```bash
git submodule update --init --recursive
```

Подмодуль закреплён на конкретном коммите части — это и есть смысл: курсовая ссылается на то
состояние кода, которое описано в её документах. Подтянуть свежее:

```bash
git submodule update --remote src/MedAppServer
```

## Документация

| где | что |
|---|---|
| [docs/](docs) | документы курсовой: ТЗ, описание программы, текст программы |
| [docs/plans/](docs/plans) | планы, по которым шёл рефакторинг: фичи с критериями, устройство, ход работ |
| [MedApp-Server/README](https://github.com/Kert0n/MedApp-Server#readme) | что умеет сервер, как запустить, перечень эндпоинтов |
| [MedApp-Server/ARCHITECTURE.md](https://github.com/Kert0n/MedApp-Server/blob/main/ARCHITECTURE.md) | модель, слои, доступ, конкурентность, синхронизация |

## История

Этот репозиторий создан при разделении на части. История разработки — в `MedApp-Server`: он и есть
прежний `MedApp`, переименованный, чтобы вся история, PR и issue остались при коде, который они
описывают.
