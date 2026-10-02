package com.mikey.reservation;

import com.mikey.reservation.support.PostgresSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import javax.sql.DataSource;
import java.sql.*;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

@SpringBootTest
class PostgresConcurrencyIT extends PostgresSupport {
    @Autowired DataSource dataSource;
    @Autowired JdbcTemplate jdbc;

    UUID resource() {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into resources(id,name,created_at,updated_at) values (?,'Database test',now(),now())", id);
        return id;
    }

    void insert(Connection connection, UUID resource, String start, String end) throws SQLException {
        try (var statement = connection.prepareStatement(
                "insert into bookings(id,resource_id,starts_at,ends_at,status,created_at) values (?,?,?::timestamptz,?::timestamptz,'CONFIRMED',now())")) {
            statement.setObject(1, UUID.randomUUID());
            statement.setObject(2, resource);
            statement.setString(3, start);
            statement.setString(4, end);
            statement.executeUpdate();
        }
    }

    int pid(Connection connection) throws SQLException {
        try (var statement = connection.createStatement(); var result = statement.executeQuery("select pg_backend_pid()")) {
            result.next(); return result.getInt(1);
        }
    }

    @Test void uncommittedWinnerBlocksCompetitorThenConflictAfterCommit() throws Exception { controlledRace(true, false); }
    @Test void rolledBackWinnerAllowsCompetitorToCommit() throws Exception { controlledRace(false, false); }
    @Test void uncommittedCancellationAllowsReplacementAfterCommit() throws Exception { controlledRace(true, true); }
    @Test void rolledBackCancellationKeepsOriginalBookingProtected() throws Exception { controlledRace(false, true); }

    void controlledRace(boolean commitFirst, boolean cancellation) throws Exception {
        UUID resource = resource();
        if (cancellation) {
            try (var connection = dataSource.getConnection()) {
                insert(connection, resource, "2030-01-02T06:00:00Z", "2030-01-02T07:00:00Z");
            }
        }
        var worker = Executors.newSingleThreadExecutor();
        // Connections are separate; fixture resource is already committed and visible.
        try (var first = dataSource.getConnection(); var second = dataSource.getConnection()) {
            first.setAutoCommit(false);
            second.setAutoCommit(false);
            try {
                try (var statement = second.createStatement()) { statement.execute("set local statement_timeout = '10s'"); }
                if (cancellation) {
                    try (var statement = first.prepareStatement("update bookings set status='CANCELLED',cancelled_at=now() where resource_id=?")) {
                        statement.setObject(1, resource); statement.executeUpdate();
                    }
                } else insert(first, resource, "2030-01-02T06:00:00Z", "2030-01-02T07:00:00Z");
                int firstPid = pid(first), secondPid = pid(second);
                Future<String> competitor = worker.submit(() -> {
                    try {
                        insert(second, resource, "2030-01-02T06:30:00Z", "2030-01-02T07:30:00Z");
                        second.commit();
                        return "COMMITTED";
                    } catch (SQLException error) {
                        second.rollback();
                        return error.getSQLState();
                    }
                });
                // Observe an actual PostgreSQL wait, not a timing assumption based on sleep.
                await().atMost(Duration.ofSeconds(5)).pollInterval(Duration.ofMillis(20)).until(() ->
                        Boolean.TRUE.equals(jdbc.queryForObject("select ? = any(pg_blocking_pids(?))", Boolean.class, firstPid, secondPid)));
                if (commitFirst) first.commit(); else first.rollback();
                boolean shouldCommit = cancellation == commitFirst;
                assertThat(competitor.get(10, TimeUnit.SECONDS)).isEqualTo(shouldCommit ? "COMMITTED" : "23P01");
                assertThat(jdbc.queryForObject("select count(*) from bookings where resource_id=? and status='CONFIRMED'",
                        Long.class, resource)).isEqualTo(1);
            } finally {
                first.rollback();
                worker.shutdownNow();
                assertThat(worker.awaitTermination(12, TimeUnit.SECONDS)).isTrue();
                second.rollback();
            }
        } finally { worker.shutdownNow(); }
    }

    @Test void directWritesCannotBypassExclusionAndCancelledRowsDoNotBlock() throws Exception {
        UUID resource = resource();
        try (var connection = dataSource.getConnection()) {
            insert(connection, resource, "2030-01-02T06:00:00Z", "2030-01-02T08:00:00Z");
            assertThatThrownBy(() -> insert(connection, resource, "2030-01-02T07:00:00Z", "2030-01-02T07:30:00Z"))
                    .isInstanceOfSatisfying(SQLException.class, error -> assertThat(error.getSQLState()).isEqualTo("23P01"));
            jdbc.update("update bookings set status='CANCELLED',cancelled_at=now() where resource_id=?", resource);
            insert(connection, resource, "2030-01-02T06:00:00Z", "2030-01-02T08:00:00Z");
        }
        assertThat(jdbc.queryForObject("select count(*) from bookings where resource_id=?", Long.class, resource)).isEqualTo(2);
    }

    @Test void databaseRejectsInvalidIntervalsInfinityAndMissingResource() throws Exception {
        UUID resource = resource();
        try (var connection = dataSource.getConnection()) {
            for (String[] values : new String[][] {
                    {"2030-01-02T06:00:00Z", "2030-01-02T06:00:00Z"},
                    {"2030-01-02T07:00:00Z", "2030-01-02T06:00:00Z"},
                    {"2030-01-02T06:00:00Z", "infinity"}}) {
                assertThatThrownBy(() -> insert(connection, resource, values[0], values[1]))
                        .isInstanceOf(SQLException.class);
            }
            assertThatThrownBy(() -> insert(connection, UUID.randomUUID(), "2030-01-02T06:00:00Z", "2030-01-02T07:00:00Z"))
                    .isInstanceOfSatisfying(SQLException.class, error -> assertThat(error.getSQLState()).isEqualTo("23503"));
        }
        assertThat(jdbc.queryForObject("select count(*) from bookings where resource_id=?", Long.class, resource)).isZero();
    }

    @Test void databaseRejectsInvalidCancellationState() throws Exception {
        UUID resource = resource();
        try (var connection = dataSource.getConnection()) {
            insert(connection, resource, "2030-01-02T06:00:00Z", "2030-01-02T07:00:00Z");
            try (var statement = connection.prepareStatement("update bookings set status='CANCELLED' where resource_id=?")) {
                statement.setObject(1, resource);
                assertThatThrownBy(statement::executeUpdate).isInstanceOfSatisfying(SQLException.class,
                        error -> assertThat(error.getSQLState()).isEqualTo("23514"));
            }
        }
    }

    @org.junit.jupiter.api.AfterEach
    void noConfirmedPairsOverlap() {
        assertThat(jdbc.queryForObject("""
                select count(*) from bookings a join bookings b
                on a.resource_id=b.resource_id and a.id < b.id
                where a.status='CONFIRMED' and b.status='CONFIRMED'
                  and a.starts_at < b.ends_at and b.starts_at < a.ends_at
                """, Long.class)).isZero();
    }
}

