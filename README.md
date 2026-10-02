# Reservation — сервис бронирования ресурсов

Java 21 · Spring Boot 4.1.1 · PostgreSQL · JPA · Liquibase

Первая версия: ресурсы, создание/просмотр/отмена броней, поиск свободного времени,
валидация, единые ошибки и защита от двойного бронирования.

## Запуск в IntelliJ IDEA

1. Открой проект и обнови Maven.
2. В конфигурации запуска ReservationApplication выбери JDK 21.
3. В Environment variables задай DB_PASSWORD — пароль reservation_app.
4. Запусти ReservationApplication.
5. Открой http://localhost:8080/actuator/health — ожидается {"status":"UP"}.

По умолчанию используется твоя БД reservation на localhost:5432, пользователь
reservation_app. Liquibase автоматически создаёт таблицы resources, bookings,
свои служебные таблицы и расширение btree_gist. Пользователь должен иметь права
на создание объектов. Существующие данные не очищаются.

Это REST API, страницы сайта на / нет. MVP не имеет пользователей и авторизации,
поэтому сервер слушает только 127.0.0.1.

## Запуск из PowerShell

Из каталога проекта:

~~~powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21.0.12.1'
$env:DB_PASSWORD = 'пароль пользователя reservation_app'
.\mvnw.cmd spring-boot:run
~~~

Настройка Environment variables в IDEA относится к запуску приложения из IDEA.
Она НЕ задаёт пароль в отдельно открытом терминале.

Можно переопределить DB_URL, DB_USERNAME и SERVER_PORT переменными окружения.
Пароль не добавляй в Git и не помещай в примеры запросов.

## Проверка вручную

При запущенном приложении:

~~~powershell
.\scripts\smoke.ps1
~~~

Сценарий создаёт демонстрационный ресурс, бронирует час завтра, проверяет конфликт,
показывает доступность, отменяет бронь и проверяет повторную отмену.
Демонстрационный ресурс и отмененная бронь остаются в БД для просмотра.

## API

Префикс: /api/v1. JSON; время с обязательным Z или смещением +04:00.
Ответы нормализуются в UTC. resourceId и bookingId — UUID.

| Метод | Адрес | Результат |
|---|---|---|
| POST | /resources | Создание, 201 и Location |
| GET | /resources/{id} | Ресурс |
| GET | /resources?page=0&size=20 | Список |
| PUT | /resources/{id} | Замена имени/описания/местоположения |
| POST | /resources/{resourceId}/bookings | Бронь, 201 и Location |
| GET | /bookings/{id} | Бронь |
| GET | /resources/{resourceId}/bookings | Список броней |
| POST | /bookings/{id}/cancel | Отмена, 200 |
| GET | /resources/{resourceId}/availability | Свободные интервалы |

Ресурс:

~~~json
{"name":"Переговорная А","description":"Для встреч","location":"Второй этаж"}
~~~

Создание брони (подставь будущее время):

~~~json
{"startsAt":"2030-01-02T10:00:00+04:00","endsAt":"2030-01-02T11:00:00+04:00"}
~~~

Параметры списка броней: page, size (1–100), status=CONFIRMED или CANCELLED,
необязательная пара from/to. Фильтр означает пересечение окна, не только начало
внутри него. Порядок startsAt ASC, id ASC. Ответ: items, page, size, totalElements.
Ресурсы отсортированы по name,id.

Доступность: обязательные from/to, необязательный minDurationMinutes (по умолчанию 1).
Например /resources/{id}/availability?from=2030-01-02T06:00:00Z&to=2030-01-02T14:00:00Z&minDurationMinutes=60.
В URL знак + в смещении кодируй как %2B или используй Z.
Ответ содержит resourceId, from, to, intervals с startsAt/endsAt.
Свободные интервалы максимальные; поиск сам по себе ничего не резервирует.
Можно просматривать историю; создать бронь в прошлом нельзя.

## Правила

- Интервалы [начало, конец): 10–11 и 11–12 совместимы.
- Длительность от 1 минуты до 24 часов.
- Начало не в прошлом, конец не далее чем через 365 дней.
- Точность до 6 знаков дробной части секунды.
- Окно поиска доступности не более 31 суток.
- Бронь сразу CONFIRMED. Отмена переводит её в CANCELLED.
- Отменять можно только до начала. Повторная отмена возвращает прежний результат.
- Удаление ресурса, перенос брони, пользователи и удержания пока не реализованы.

Лимиты задаются booking.policy.* в application.properties.

## Ошибки

Формат application/problem+json: type, title, status, detail, instance, code,
requestId. Ошибки полей также содержат errors. Идентификатор запроса возвращается
в X-Request-ID и пишется в лог.

| Статус | Код / причина |
|---|---|
| 400 | INVALID_REQUEST / VALIDATION_FAILED |
| 404 | RESOURCE_NOT_FOUND / BOOKING_NOT_FOUND |
| 409 | BOOKING_OVERLAP / BOOKING_CANNOT_BE_CANCELLED |
| 503 | TEMPORARILY_UNAVAILABLE |
| 500 | INTERNAL_ERROR |

При конфликте источник истины — PostgreSQL exclusion constraint, а не поиск
доступности. Создание дополнительно блокирует строку ресурса для предсказуемой
обработки параллельных запросов. Подробности в docs/architecture.md.

## Тесты

~~~powershell
# Быстрые unit-тесты, база и пароль не нужны:
.\mvnw.cmd test

# Все тесты и сборка, PostgreSQL должен быть запущен:
$env:DB_PASSWORD = 'пароль пользователя reservation_app'
.\mvnw.cmd verify
~~~

Docker не нужен. По твоему выбору интеграционные тесты используют ту же базу
reservation, но отдельную случайную схему reservation_it_*.
Схема удаляется при нормальном завершении тестового JVM; рабочие таблицы public
не очищаются. При принудительном завершении процесса временная схема может остаться.
Не удаляй public. Расширение btree_gist остаётся установленным в public.

Surefire запускает unit-тесты в фазе test и *IT в фазе integration-test команды verify.
Отчёты: target/surefire-reports и target/integration-test-reports.
Без подключения к БД verify завершается ошибкой — тесты не пропускаются.

Проверяются 20 раундов по 8 конкурирующих HTTP-запросов, разные ресурсы и интервалы,
отмена, откат транзакций и реальные ожидания блокировок PostgreSQL.

## Сборка JAR

~~~powershell
.\mvnw.cmd verify
java -jar target\reservation-0.0.1-SNAPSHOT.jar
~~~

Для запуска JAR также нужна DB_PASSWORD. Не запускай второй экземпляр на уже занятом
порту; для проверки можно задать SERVER_PORT=18080.

## Следующие этапы

Пользователи/роли → расписания → удержания → лист ожидания → уведомления → календари.
Сейчас очередей, кэша и микросервисов нет; они добавляются под конкретные задачи.


Границы входных дат: UTC годы 0001–9999; даты в JSON — ISO-строки, не epoch-числа. Нулевой символ U+0000 в текстовых полях запрещен.
