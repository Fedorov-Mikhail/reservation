# Reservation — статус реализации

Рабочая ветка: feature/reservation-roadmap.

Реализованы ресурсы и удаление без истории; архивирование/восстановление с защитой будущих броней и заявок; поиск; календарь дня/недели; учётные записи и права; расписания; удержания; очередь; внутренние уведомления; email-предпочтения, durable delivery и напоминания; Google OAuth и односторонняя синхронизация; транзакционный аудит; UI-повторы с Idempotency-Key; CI, Dockerfiles, Compose, readiness и метрики.

Проверки 12 октября 2026: 29 backend unit + 57 PostgreSQL integration tests, 23 frontend tests, production build, 4 Playwright scenarios. Compose config проверен. Временные схемы тестов удаляются штатным завершением JVM/скрипта; рабочие данные public не очищаются.

Независимое ревью выявило гонку первого OAuth callback с disconnect и несовместимую Windows-команду в Ubuntu CI. Гонка воспроизведена тестом disconnectDuringFirstConnectionInsertInvalidatesLateTokens RED→GREEN; исправлена блокировкой стабильной строки пользователя. CI выбирает npm по ОС. Противоречивое пустое состояние недельного календаря воспроизведено CalendarWeek.test.tsx RED→GREEN и исправлено. Финальный полный backend-прогон и browser-сценарии проходят.

Ограничения: реальные SMTP/Google credentials не настроены автоматически; тесты используют fake-provider. Движок Docker на локальном компьютере не запущен, поэтому образы локально не собирались. HTTP-сессии одного backend; горизонтальное масштабирование требует общего хранилища либо sticky routing. Внешние календарные события сохраняются при disconnect; SMTP после потери подтверждения может прислать повтор. Кэш, двусторонняя синхронизация, перенос брони и изоляция организаций — дальнейшие задачи.

Инструкции: основной README, frontend/README.md, docs/email.md, docs/google-calendar.md, docs/operations.md.
