package com.mikey.reservation;

import com.mikey.reservation.support.PostgresSupport;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(ReservationApiIT.FixedTime.class)
class ReservationApiIT extends PostgresSupport {
    @Value("${local.server.port}") int port;
    final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    final JsonMapper json = JsonMapper.builder().build();

    @TestConfiguration
    static class FixedTime {
        @Bean @Primary Clock testClock() {
            return Clock.fixed(Instant.parse("2030-01-01T00:00:00Z"), ZoneOffset.UTC);
        }
    }

    HttpResponse<String> call(String method, String path, String body) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(20)).header("Content-Type", "application/json");
        return client.send(builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    String resource() throws Exception {
        var response = call("POST", "/api/v1/resources", "{\"name\":\"Meeting room\"}");
        assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
        return json.readTree(response.body()).path("id").asText();
    }

    @Test
    void bookingLifecycleAndAvailability() throws Exception {
        String resource = resource();
        String path = "/api/v1/resources/" + resource;
        var created = call("POST", path + "/bookings",
                "{\"startsAt\":\"2030-01-02T10:00:00+04:00\",\"endsAt\":\"2030-01-02T11:00:00+04:00\"}");
        assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
        JsonNode booking = json.readTree(created.body());
        assertThat(booking.path("startsAt").asText()).isEqualTo("2030-01-02T06:00:00Z");
        String location = created.headers().firstValue("Location").orElseThrow();
        assertThat(call("GET", location, null).statusCode()).isEqualTo(200);
        var conflict = call("POST", path + "/bookings",
                "{\"startsAt\":\"2030-01-02T06:30:00Z\",\"endsAt\":\"2030-01-02T07:30:00Z\"}");
        assertThat(conflict.statusCode()).as(conflict.body()).isEqualTo(409);
        assertThat(json.readTree(conflict.body()).path("code").asText()).isEqualTo("BOOKING_OVERLAP");
        var free = json.readTree(call("GET", path
                + "/availability?from=2030-01-02T05:00:00Z&to=2030-01-02T08:00:00Z&minDurationMinutes=60", null).body());
        assertThat(free.path("intervals").size()).isEqualTo(2);
        var cancelled = call("POST", location + "/cancel", null);
        assertThat(cancelled.statusCode()).as(cancelled.body()).isEqualTo(200);
        assertThat(json.readTree(cancelled.body()).path("status").asText()).isEqualTo("CANCELLED");
        assertThat(call("POST", location + "/cancel", null).body()).isEqualTo(cancelled.body());
        assertThat(call("POST", path + "/bookings",
                "{\"startsAt\":\"2030-01-02T06:00:00Z\",\"endsAt\":\"2030-01-02T07:00:00Z\"}").statusCode()).isEqualTo(201);
    }

    @org.springframework.beans.factory.annotation.Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

    String body(String start, String end) {
        return "{\"startsAt\":\"" + start + "\",\"endsAt\":\"" + end + "\"}";
    }

    HttpResponse<String> book(String resource, String start, String end) throws Exception {
        return call("POST", "/api/v1/resources/" + resource + "/bookings", body(start, end));
    }

    String bookingId(HttpResponse<String> response) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
        return json.readTree(response.body()).path("id").asText();
    }

    void error(HttpResponse<String> response, int status, String code) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
        assertThat(response.headers().firstValue("Content-Type").orElse("")).contains("application/problem+json");
        JsonNode problem = json.readTree(response.body());
        assertThat(problem.path("status").asInt()).isEqualTo(status);
        assertThat(problem.path("code").asText()).isEqualTo(code);
        assertThat(problem.path("requestId").asText()).isEqualTo(response.headers().firstValue("X-Request-ID").orElseThrow());
        assertThat(response.body()).doesNotContain("PSQLException", "stackTrace", "password");
    }

    long confirmed(String resource) {
        return jdbc.queryForObject("select count(*) from bookings where resource_id = ? and status = 'CONFIRMED'",
                Long.class, java.util.UUID.fromString(resource));
    }

    java.util.List<HttpResponse<String>> parallel(int count, java.util.function.IntFunction<java.util.concurrent.Callable<HttpResponse<String>>> task)
            throws Exception {
        var ready = new java.util.concurrent.CountDownLatch(count);
        var start = new java.util.concurrent.CountDownLatch(1);
        var executor = java.util.concurrent.Executors.newFixedThreadPool(count);
        try {
            var futures = new java.util.ArrayList<java.util.concurrent.Future<HttpResponse<String>>>();
            for (int i = 0; i < count; i++) {
                var action = task.apply(i);
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, java.util.concurrent.TimeUnit.SECONDS)) throw new AssertionError("Start gate timed out");
                    return action.call();
                }));
            }
            assertThat(ready.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            start.countDown();
            var responses = new java.util.ArrayList<HttpResponse<String>>();
            for (var future : futures) responses.add(future.get(25, java.util.concurrent.TimeUnit.SECONDS));
            return responses;
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void concurrentOverlappingRequestsCreateExactlyOneBooking() throws Exception {
        for (int round = 0; round < 20; round++) {
            String resource = resource();
            var responses = parallel(8, i -> () -> book(resource, "2030-01-02T06:00:00Z", "2030-01-02T07:00:00Z"));
            assertThat(responses.stream().filter(r -> r.statusCode() == 201).count()).as("round " + round).isEqualTo(1);
            for (var response : responses) if (response.statusCode() != 201) error(response, 409, "BOOKING_OVERLAP");
            assertThat(confirmed(resource)).isEqualTo(1);
            String winner = bookingId(responses.stream().filter(r -> r.statusCode() == 201).findFirst().orElseThrow());
            assertThat(jdbc.queryForObject("select id::text from bookings where resource_id = ?",
                    String.class, java.util.UUID.fromString(resource))).isEqualTo(winner);
        }
    }

    @Test
    void differentIntervalsWithCommonIntersectionHaveOneWinner() throws Exception {
        String resource = resource();
        var responses = parallel(8, i -> () -> book(resource, "2030-01-02T06:0" + i + ":00Z", "2030-01-02T07:00:00Z"));
        assertThat(responses.stream().filter(r -> r.statusCode() == 201).count()).isEqualTo(1);
        for (var response : responses) if (response.statusCode() != 201) error(response, 409, "BOOKING_OVERLAP");
        assertThat(confirmed(resource)).isEqualTo(1);
    }

    @Test
    void independentIntervalGroupsHaveTwoWinners() throws Exception {
        String resource = resource();
        var responses = parallel(8, i -> () -> i % 2 == 0
                ? book(resource, "2030-01-02T06:00:00Z", "2030-01-02T07:00:00Z")
                : book(resource, "2030-01-02T08:00:00Z", "2030-01-02T09:00:00Z"));
        assertThat(responses.stream().filter(r -> r.statusCode() == 201).count()).isEqualTo(2);
        for (var response : responses) if (response.statusCode() != 201) error(response, 409, "BOOKING_OVERLAP");
        assertThat(confirmed(resource)).isEqualTo(2);
    }

    @Test
    void adjacentIntervalsAndDifferentResourcesDoNotConflict() throws Exception {
        String first = resource();
        String second = resource();
        var responses = parallel(3, i -> () -> switch (i) {
            case 0 -> book(first, "2030-01-02T06:00:00Z", "2030-01-02T07:00:00Z");
            case 1 -> book(first, "2030-01-02T07:00:00Z", "2030-01-02T08:00:00Z");
            default -> book(second, "2030-01-02T06:00:00Z", "2030-01-02T07:00:00Z");
        });
        assertThat(responses).allSatisfy(r -> assertThat(r.statusCode()).as(r.body()).isEqualTo(201));
        assertThat(confirmed(first)).isEqualTo(2);
        assertThat(confirmed(second)).isEqualTo(1);
    }

    @Test
    void concurrentCancellationIsIdempotent() throws Exception {
        String resource = resource();
        String id = bookingId(book(resource, "2030-01-02T06:00:00Z", "2030-01-02T07:00:00Z"));
        var responses = parallel(8, i -> () -> call("POST", "/api/v1/bookings/" + id + "/cancel", null));
        assertThat(responses).allSatisfy(r -> assertThat(r.statusCode()).as(r.body()).isEqualTo(200));
        assertThat(responses.stream().map(HttpResponse::body).distinct().count()).isEqualTo(1);
        assertThat(confirmed(resource)).isZero();
    }

    @Test
    void cancellationRacingWithCreationPreservesInvariant() throws Exception {
        for (int round = 0; round < 10; round++) {
            String resource = resource();
            String old = bookingId(book(resource, "2030-01-02T06:00:00Z", "2030-01-02T07:00:00Z"));
            var responses = parallel(2, i -> () -> i == 0
                    ? call("POST", "/api/v1/bookings/" + old + "/cancel", null)
                    : book(resource, "2030-01-02T06:00:00Z", "2030-01-02T07:00:00Z"));
            assertThat(responses.get(0).statusCode()).isEqualTo(200);
            assertThat(responses.get(1).statusCode()).as(responses.get(1).body()).isIn(201, 409);
            assertThat(confirmed(resource)).isLessThanOrEqualTo(1);
            if (responses.get(1).statusCode() == 409) {
                error(responses.get(1), 409, "BOOKING_OVERLAP");
                assertThat(book(resource, "2030-01-02T06:00:00Z", "2030-01-02T07:00:00Z").statusCode()).isEqualTo(201);
            }
        }
    }

    @Test
    void resourceUpdatePaginationAndBookingFilters() throws Exception {
        String id = resource();
        var update = call("PUT", "/api/v1/resources/" + id,
                "{\"name\":\"  Updated room  \",\"location\":\"Floor 2\"}");
        assertThat(update.statusCode()).isEqualTo(200);
        assertThat(json.readTree(update.body()).path("name").asText()).isEqualTo("Updated room");
        assertThat(call("GET", "/api/v1/resources/" + id, null).body()).isEqualTo(update.body());
        var page = call("GET", "/api/v1/resources?page=0&size=1", null);
        assertThat(page.statusCode()).isEqualTo(200);
        assertThat(json.readTree(page.body()).path("items").size()).isEqualTo(1);
        String booking = bookingId(book(id, "2030-01-02T06:00:00Z", "2030-01-02T07:00:00Z"));
        String path = "/api/v1/resources/" + id + "/bookings";
        var all = call("GET", path, null);
        assertThat(all.statusCode()).as(all.body()).isEqualTo(200);
        assertThat(json.readTree(all.body()).path("totalElements").asInt()).isEqualTo(1);
        var overlaps = call("GET", path + "?from=2030-01-02T06:30:00Z&to=2030-01-02T08:00:00Z", null);
        assertThat(overlaps.statusCode()).as(overlaps.body()).isEqualTo(200);
        assertThat(json.readTree(overlaps.body()).path("items").size()).isEqualTo(1);
        var adjacent = call("GET", path + "?from=2030-01-02T07:00:00Z&to=2030-01-02T08:00:00Z", null);
        assertThat(json.readTree(adjacent.body()).path("items").size()).isZero();
        call("POST", "/api/v1/bookings/" + booking + "/cancel", null);
        assertThat(json.readTree(call("GET", path + "?status=CONFIRMED", null).body()).path("items").size()).isZero();
        assertThat(json.readTree(call("GET", path + "?status=CANCELLED", null).body()).path("items").size()).isEqualTo(1);
    }

    @Test
    void validationAndMissingObjectsUseProblemDetails() throws Exception {
        error(call("POST", "/api/v1/resources", "{\"name\":\"   \"}"), 400, "VALIDATION_FAILED");
        error(call("POST", "/api/v1/resources", "{"), 400, "INVALID_REQUEST");
        error(call("GET", "/api/v1/resources/not-a-uuid", null), 400, "INVALID_REQUEST");
        error(call("GET", "/api/v1/resources/" + java.util.UUID.randomUUID(), null), 404, "RESOURCE_NOT_FOUND");
        error(call("GET", "/api/v1/bookings/" + java.util.UUID.randomUUID(), null), 404, "BOOKING_NOT_FOUND");
        error(call("POST", "/api/v1/bookings/" + java.util.UUID.randomUUID() + "/cancel", null), 404, "BOOKING_NOT_FOUND");
        for (String query : java.util.List.of("size=0", "size=101", "page=-1", "page=2147483647&size=100"))
            error(call("GET", "/api/v1/resources?" + query, null), 400, "VALIDATION_FAILED");
        String resource = resource();
        String path = "/api/v1/resources/" + resource;
        error(call("POST", path + "/bookings", "{}"), 400, "VALIDATION_FAILED");
        error(book(resource, "2030-01-02T06:00:00", "2030-01-02T07:00:00"), 400, "INVALID_REQUEST");
        error(book(resource, "2030-01-02T06:00:00Z", "2030-01-02T06:00:00Z"), 400, "VALIDATION_FAILED");
        error(book(resource, "2029-12-31T06:00:00Z", "2029-12-31T07:00:00Z"), 400, "VALIDATION_FAILED");
        error(book(resource, "2030-01-02T06:00:00Z", "2030-01-02T06:00:59Z"), 400, "VALIDATION_FAILED");
        error(book(resource, "2030-01-02T06:00:00Z", "2030-01-03T06:00:01Z"), 400, "VALIDATION_FAILED");
        error(book(resource, "2031-01-01T06:00:00Z", "2031-01-01T07:00:00Z"), 400, "VALIDATION_FAILED");
        error(book(resource, "2030-01-02T06:00:00.0000001Z", "2030-01-02T07:00:00Z"), 400, "VALIDATION_FAILED");
        error(call("GET", path + "/bookings?from=2030-01-02T06:00:00Z", null), 400, "VALIDATION_FAILED");
        error(call("GET", path + "/bookings?status=INVALID", null), 400, "INVALID_REQUEST");
        error(call("GET", path + "/availability?from=2030-01-01T00:00:00Z&to=2030-02-02T00:00:00Z", null), 400, "VALIDATION_FAILED");
        error(call("GET", path + "/availability?from=2030-01-02T06:00:00Z&to=2030-01-02T08:00:00Z&minDurationMinutes=0", null), 400, "VALIDATION_FAILED");
        assertThat(confirmed(resource)).isZero();
    }

    @Test
    void availabilityClipsBookingsAndDoesNotReserveTime() throws Exception {
        String resource = resource();
        String base = "/api/v1/resources/" + resource + "/availability?from=2030-01-02T06:00:00Z&to=2030-01-02T10:00:00Z";
        bookingId(book(resource, "2030-01-02T05:00:00Z", "2030-01-02T07:00:00Z"));
        bookingId(book(resource, "2030-01-02T09:00:00Z", "2030-01-02T11:00:00Z"));
        var free = call("GET", base, null);
        assertThat(free.statusCode()).isEqualTo(200);
        var intervals = json.readTree(free.body()).path("intervals");
        assertThat(intervals.size()).isEqualTo(1);
        assertThat(intervals.get(0).path("startsAt").asText()).isEqualTo("2030-01-02T07:00:00Z");
        assertThat(intervals.get(0).path("endsAt").asText()).isEqualTo("2030-01-02T09:00:00Z");
        assertThat(json.readTree(call("GET", base + "&minDurationMinutes=121", null).body()).path("intervals").size()).isZero();
        bookingId(book(resource, "2030-01-02T07:00:00Z", "2030-01-02T09:00:00Z"));
        error(book(resource, "2030-01-02T07:00:00Z", "2030-01-02T09:00:00Z"), 409, "BOOKING_OVERLAP");
        assertThat(json.readTree(call("GET", base, null).body()).path("intervals").size()).isZero();
    }

    @Test
    void microsecondPrecisionRoundTripsAndContainedIntervalConflicts() throws Exception {
        String resource = resource();
        var saved = book(resource, "2030-01-02T06:00:00.123456Z", "2030-01-02T08:00:00.123456Z");
        String id = bookingId(saved);
        var read = json.readTree(call("GET", "/api/v1/bookings/" + id, null).body());
        assertThat(read.path("startsAt").asText()).isEqualTo("2030-01-02T06:00:00.123456Z");
        error(book(resource, "2030-01-02T07:00:00Z", "2030-01-02T07:30:00Z"), 409, "BOOKING_OVERLAP");
        assertThat(confirmed(resource)).isEqualTo(1);
    }

    @Test
    void startedBookingCannotBeCancelled() throws Exception {
        String resource = resource();
        var id = java.util.UUID.randomUUID();
        jdbc.update("insert into bookings(id,resource_id,starts_at,ends_at,status,created_at) values (?,?,?::timestamptz,?::timestamptz,'CONFIRMED',now())",
                id, java.util.UUID.fromString(resource), "2029-12-31T23:00:00Z", "2030-01-01T01:00:00Z");
        error(call("POST", "/api/v1/bookings/" + id + "/cancel", null), 409, "BOOKING_CANNOT_BE_CANCELLED");
        assertThat(confirmed(resource)).isEqualTo(1);
    }

    @Test
    void healthAndRequestCorrelationWork() throws Exception {
        assertThat(call("GET", "/actuator/health", null).statusCode()).isEqualTo(200);
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/resources"))
                .header("X-Request-ID", "manual-test-123").GET().build();
        assertThat(client.send(request, HttpResponse.BodyHandlers.ofString()).headers().firstValue("X-Request-ID"))
                .contains("manual-test-123");
    }


    @Test
    void extremeQueryDatesAreRejectedBeforeJdbc() throws Exception {
        String resource = resource();
        String window = "?from=%2B999999999-01-01T00:00:00Z&to=%2B999999999-01-02T00:00:00Z";
        error(call("GET", "/api/v1/resources/" + resource + "/availability" + window, null), 400, "VALIDATION_FAILED");
        error(call("GET", "/api/v1/resources/" + resource + "/bookings" + window, null), 400, "VALIDATION_FAILED");
    }

    @Test
    void postgresUnsupportedNulIsRejectedInAllResourceFields() throws Exception {
        String resource = resource();
        for (String field : java.util.List.of("name", "description", "location")) {
            // Build an escaped JSON NUL without embedding a Unicode escape in Java source.
            String nul = "\\" + "u0000";
            String request = field.equals("name") ? "{\"name\":\"bad" + nul + "value\"}"
                    : "{\"name\":\"Valid\",\"" + field + "\":\"bad" + nul + "value\"}";
            error(call("POST", "/api/v1/resources", request), 400, "VALIDATION_FAILED");
            error(call("PUT", "/api/v1/resources/" + resource, request), 400, "VALIDATION_FAILED");
        }
        assertThat(json.readTree(call("GET", "/api/v1/resources/" + resource, null).body()).path("name").asText()).isEqualTo("Meeting room");
    }

    @Test
    void numericJsonTimestampsAreNotAcceptedAsEpochSeconds() throws Exception {
        String resource = resource();
        long start = Instant.parse("2030-01-02T06:00:00Z").getEpochSecond();
        var result = call("POST", "/api/v1/resources/" + resource + "/bookings",
                "{\"startsAt\":" + start + ",\"endsAt\":" + (start + 3600) + "}");
        error(result, 400, "INVALID_REQUEST");
        assertThat(confirmed(resource)).isZero();
    }

    @org.springframework.beans.factory.annotation.Autowired javax.sql.DataSource dataSource;
    @Test
    void lockedResourceReturns503WithoutCreatingAPartialBooking() throws Exception {
        String resource = resource();
        try (var connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (var statement = connection.prepareStatement("select id from resources where id=? for update")) {
                statement.setObject(1, java.util.UUID.fromString(resource));
                try (var result = statement.executeQuery()) { assertThat(result.next()).isTrue(); }
                error(book(resource, "2030-01-02T06:00:00Z", "2030-01-02T07:00:00Z"), 503, "TEMPORARILY_UNAVAILABLE");
            } finally { connection.rollback(); }
        }
        assertThat(confirmed(resource)).isZero();
        assertThat(book(resource, "2030-01-02T06:00:00Z", "2030-01-02T07:00:00Z").statusCode()).isEqualTo(201);
    }


    @Test
    void exhaustedConnectionPoolReturns503() throws Exception {
        String resource = resource();
        var connections = new java.util.ArrayList<java.sql.Connection>();
        int poolSize = dataSource.unwrap(com.zaxxer.hikari.HikariDataSource.class).getMaximumPoolSize();
        try {
            for (int i = 0; i < poolSize; i++) connections.add(dataSource.getConnection());
            error(book(resource, "2030-01-02T06:00:00Z", "2030-01-02T07:00:00Z"), 503, "TEMPORARILY_UNAVAILABLE");
        } finally {
            for (var connection : connections) connection.close();
        }
        assertThat(confirmed(resource)).isZero();
    }

}
