package com.connectone;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Wipes and re-migrates the dedicated test database before the Spring context starts using it.
 * Guarded: it refuses to run unless the JDBC URL targets the ConnectOne test database.
 */
@TestConfiguration
class TestDatabaseConfiguration {

    static final String TEST_URL = "jdbc:postgresql://localhost:5434/connectone_test";

    @Bean
    FlywayMigrationStrategy cleanThenMigrate(@Value("${spring.datasource.url}") String url) {
        if (!TEST_URL.equals(url)) {
            throw new IllegalStateException("Refusing to clean a database other than connectone_test: " + url);
        }
        return flyway -> {
            flyway.clean();
            flyway.migrate();
        };
    }
}
