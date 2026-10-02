package com.mikey.reservation.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import java.sql.DriverManager;
import java.util.UUID;

/** Real PostgreSQL, isolated schema in the developer-selected database. No test data in public. */
public abstract class PostgresSupport {
    private static final String URL = env("DB_URL", "jdbc:postgresql://localhost:5432/reservation");
    private static final String USER = env("DB_USERNAME", "reservation_app");
    private static final String PASSWORD = System.getenv("DB_PASSWORD");
    private static final String SCHEMA = "reservation_it_" + UUID.randomUUID().toString().replace("-", "");
    private static boolean initialized;

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    @DynamicPropertySource
    static synchronized void database(DynamicPropertyRegistry registry) throws Exception {
        if (!initialized) {
            if (PASSWORD == null || PASSWORD.isBlank()) throw new IllegalStateException("Set DB_PASSWORD for PostgreSQL integration tests");
            try (var connection = DriverManager.getConnection(URL, USER, PASSWORD); var sql = connection.createStatement()) {
                sql.execute("CREATE SCHEMA " + SCHEMA);
            }
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try (var connection = DriverManager.getConnection(URL, USER, PASSWORD); var sql = connection.createStatement()) {
                    sql.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
                } catch (Exception e) {
                    System.err.println("Test schema cleanup failed: " + SCHEMA + " (" + e.getClass().getSimpleName() + ")");
                }
            }));
            initialized = true;
        }
        registry.add("spring.datasource.url", () -> URL + (URL.contains("?") ? "&" : "?") + "currentSchema=" + SCHEMA + ",public");
        registry.add("spring.datasource.username", () -> USER);
        registry.add("spring.datasource.password", () -> PASSWORD);
        registry.add("spring.liquibase.default-schema", () -> SCHEMA);
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> SCHEMA);
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> "12");
        registry.add("server.address", () -> "127.0.0.1");
    }
}

