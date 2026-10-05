# Развитие Reservation: implementation plan
> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans. Implement and verify each task before proceeding.

**Goal:** Реализовать пользователей, расписания, удержания, очередь, надёжные уведомления и календарный адаптер.
**Architecture:** Модульный монолит, PostgreSQL источник истины. Сессии и CSRF; единая блокировка ресурса перед изменением бронирований. Outbox в одной транзакции с изменением.
**Tech Stack:** Существующие Java 21/Spring Boot/PostgreSQL/Liquibase, React/TypeScript/Vite.
**Spec:** roadmap в этой беседе от 2026-10-05; точные решения ниже.

## Global Constraints
- Рабочая папка reservation; frontend изменяется только в reservation/frontend. Соседняя старая копия не синхронизируется.
- Рабочая БД reservation; интеграционные тесты — временная схема через PostgresSupport.
- Существующие данные сохраняются; старые брони принадлежат отключённому техническому пользователю.
- Никаких паролей/токенов в Git или логах.
- При сетевой ошибке скачивания остановиться и дать пользователю команду.
- Календари требуют отдельной пользовательской OAuth-конфигурации. До её предоставления проверяем адаптер локальным HTTP-сервером и явно отмечаем внешнее подключение непроверенным.

## Review Focus
- Потеря ответа POST: не повторять автоматически, идемпотентность операций.
- Конкурентные подтверждение/истечение/отмена: только один допустимый исход.
- Проверка чужих UUID и отключённых аккаунтов на каждом запросе.
- DST и конфликт редактирования расписания с бронью.
- Дубликаты/неправильный порядок событий, сохранность секретов OAuth.

## Task 1: Пользователи и роли
Files: identity/*, SecurityConfiguration, Booking/Resource services, migration 002-users.sql; frontend auth; IdentityIT.
Interfaces: CurrentUser.requiredId(), requireAdmin(), requireManager(resourceId).
Session login/logout + CSRF. Admin bootstrap only explicit environment configuration, registration only USER. Resource creation ADMIN; management assigned per resource. Own-booking access or admin, audit cancellation reason.
- [ ] RED: authentication/CSRF/ownership/admin/disabled account tests.
- [ ] Implement migrations, authentication and service authorization.
- [ ] Adapt previous HTTP integration tests to authenticated requests, retain all concurrency assertions.
- [ ] Add login/logout/register and role-aware frontend.
- [ ] GREEN: Maven verify, frontend tests/build, browser auth lifecycle; commit.

## Task 2: Расписания
Files: schedule/*, migration 003-schedules.sql, AvailabilityService, resource settings UI, ScheduleIT.
Interfaces: ScheduleService.openIntervals(resourceId, window), validateBooking(resourceId, interval).
ZoneId resource; weekly local intervals and full-day overrides; default existing resources 24/7.
- [ ] RED: breaks/exceptions/DST/full containment/concurrent change tests.
- [ ] Implement schedule changes under resource lock; reject changes violating active reservations.
- [ ] API and frontend schedule editor; GREEN suite and commit.

## Task 3: Удержания
Files: Booking state/services/repository, migration 004-holds.sql, hold controller/UI, HoldConcurrencyIT.
Interfaces: createHold, confirm, expire; all operations resource lock then booking lock.
HELD and CONFIRMED participate in same exclusion constraint; explicit EXPIRED transitions, no time predicate in index.
Expiry worker and lazy cleanup. Check deadline after acquiring locks. Idempotent creation keys bound to user and request hash.
- [ ] RED: simultaneous holds, confirm vs expiry, rollback and replay tests.
- [ ] Implement lifecycle, migrations, expiry and UI.
- [ ] GREEN repeated real PostgreSQL races; commit.

## Task 4: Очередь и outbox foundation
Files: waitlist/*, events/*, migrations 005-waitlist.sql and 006-outbox.sql, WaitlistIT.
FIFO eligible exact interval by created_at,id. Offered hold links entry.
Resource lock encloses release and next offer, preventing queue bypass.
- [ ] RED: duplicate worker, priority, expiration and cancellation tests.
- [ ] Implement queue plus transactional outbox insertion.
- [ ] UI waiting requests/offer confirmation; GREEN and commit.

## Task 5: Уведомления
Files: notifications/*, outbox worker, migration 007-notifications.sql, NotificationsIT, frontend inbox.
Unique event+recipient; persistent retries/backoff/dead-letter state. No external email channel in first iteration.
- [ ] RED: rollback/no-event, crash recovery, duplicate delivery, isolation tests.
- [ ] Implement worker, API and unread/read UI; GREEN and commit.

## Task 6: Календари
Files: calendar/*, migration 008-calendars.sql, CalendarAdapterIT, settings UI.
First provider confirmation pending; recommendation Google Calendar, one-way sync.
OAuth state bound to session; server-side token encryption using external key; stable event IDs, desired-state reconciliation after outbox.
No provider calls inside reservation transaction. Disconnect prevents subsequent jobs.
- [ ] RED: fake provider timeout-after-create, 429, token refresh, cancellation before delayed creation.
- [ ] Implement provider adapter/settings and deployment configuration.
- [ ] GREEN local adapter tests; real OAuth requires user's configuration and browser authorization.
- [ ] Final review, regression suite and update README.

## Progress
- Baseline inspected: clean Git tree; current auth disabled; four existing Liquibase changesets.
- Decisions: work in a feature branch in specified checkout; preserve local working DB and existing records.


## Checkpoint
- Backend implementation for users, schedules, holds, queue and internal notifications added; full regression: 16 unit + 33 integration tests passed (target/roadmap-regression.log).
- Frontend includes authentication, schedule editor, hold/confirm controls, queue and inbox; component suite 5/5 and build passed. Browser suite still needs authentication adaptation and isolated-schema verification.
- Test-first failures recorded: IdentityIT 3 failures, ScheduleIT 404, HoldIT no created hold, WaitlistIT missing endpoints/outbox; current backend suite green.
- Existing public schema not migrated during development; tests use temporary schemas.
- Calendar integration in progress: need OAuth2 client dependency. Real Google OAuth configuration remains user-owned and is not yet available.
- Remaining verification: expanded concurrency/permissions tests, browser suite, fresh final code review, administrator setup and updated README.
