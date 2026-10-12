# Эксплуатация и CI

## Native запуск

Обычный запуск Java/PostgreSQL и Vite описан в основном README. SMTP и Google Calendar по умолчанию выключены. Перезапуск новой версии применяет Liquibase-миграции; перед обновлением рабочей БД рекомендуется обычная резервная копия. Миграции добавляют поля архивирования, аудит, настройки/очередь email и зашифрованные подключения календаря.

## Docker Compose

Требуется запущенный Docker Engine (на Windows — Docker Desktop с Linux containers). Команды из корня репозитория:

```powershell
docker compose up --build -d
docker compose logs -f backend
docker compose stop
```

До запуска задайте `DB_PASSWORD` и, при первом запуске, `ADMIN_USERNAME`, `ADMIN_PASSWORD` в окружении. Пароли не хранятся в Compose. Compose запускает отдельный PostgreSQL с volume `postgres_data`, backend и nginx/frontend. Это отдельная БД, она не подключается к установленному на хосте PostgreSQL. Существующая native БД продолжает использоваться при обычном запуске из IDEA.

Интерфейс: http://127.0.0.1:5173. Backend: http://127.0.0.1:8080. Перед Compose остановите native процессы на этих портах. Данные сохраняются после `stop` и обычного `down`; удаление volumes удаляет контейнерную БД.

Задайте SMTP/OAuth-переменные из документации интеграций, если они нужны в контейнерах. Callback Google использует открытый порт backend. Сгенерируйте `CALENDAR_ENCRYPTION_KEY` как 32 случайных байта в Base64; сохраняйте значение между перезапусками:

```powershell
$keyBytes = [byte[]]::new(32)
[System.Security.Cryptography.RandomNumberGenerator]::Fill($keyBytes)
$env:CALENDAR_ENCRYPTION_KEY = [Convert]::ToBase64String($keyBytes)
```

## Проверка готовности и метрики

- `/actuator/health` — общий статус без деталей.
- `/actuator/health/liveness` — состояние процесса.
- `/actuator/health/readiness` — готовность приложения и PostgreSQL.
- `/actuator/metrics` — список метрик, доступен только действующему администратору через сессию.

SMTP исключён из общей health-проверки: отказ email-провайдера не блокирует бронирование. Неудачные доставки видны в административных настройках. Стандартные HTTP-метрики измеряют время и статусы запросов. Дополнительные метрики:

- `reservation.conflicts` с кодом бизнес-конфликта;
- `reservation.holds.active` и `reservation.holds.expiry.backlog`;
- `reservation.waitlist.pending`;
- `reservation.outbox.pending`;
- `reservation.resources.archived`;
- `reservation.email.pending`, `reservation.email.failed`;
- `reservation.calendar.pending`, `reservation.calendar.failed`.

В HTTP-логах и ответах присутствует X-Request-ID. Аудит записывается триггерами PostgreSQL в той же транзакции, что изменение, и содержит инициатора пользовательских действий. Системные фоновые изменения имеют actorId=null. Пароли и OAuth-токены в аудит не записываются.

## CI

Workflow `.github/workflows/ci.yml` запускается на push и pull request. Выполняет Maven verify с PostgreSQL service, компонентные проверки frontend, сборку, browser-тесты Chromium и сборку обоих Docker-образов. Артефакты содержат отчёты тестов. Данные CI являются тестовыми и не относятся к рабочей БД.

Локальный Playwright использует установленный Edge; Linux CI скачивает Chromium. Команда запуска Vite выбирается по ОС.

## Масштабирование

Сервис остаётся монолитом. Email и календарные задания используют PostgreSQL leases и SKIP LOCKED; восстановление истёкших аренд и конкурентный захват покрыты тестами. Брони и архивирование используют одинаковый порядок блокировок ресурсов. Кэш доступности пока не вводится: источник истины для создания — PostgreSQL.

Для нескольких HTTP-экземпляров требуется общее хранилище сессий либо sticky routing; текущий Compose содержит один backend. Перед увеличением количества экземпляров нужно проверить межсерверный login/CSRF, планировщики и подключения провайдеров. SMTP допускает повтор письма после принятия провайдером и сбоя до записи результата — это описано в email.md.

## Проверено локально

12 октября 2026: Maven verify — 29 unit + 57 PostgreSQL IT, frontend — 23 теста и production-сборка, Playwright — 4 сценария, Compose — config --quiet. Docker Engine на проверяемом компьютере не запущен; Docker-образы локально не собирались. Реальные SMTP и OAuth-согласие Google проверяются после настройки внешних учётных данных; автоматические тесты используют fake-провайдеры.
