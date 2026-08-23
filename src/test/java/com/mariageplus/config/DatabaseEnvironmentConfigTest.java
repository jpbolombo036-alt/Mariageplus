package com.mariageplus.config;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DatabaseEnvironmentConfigTest {

    private final DatabaseEnvironmentConfig config = new DatabaseEnvironmentConfig();

    @Test
    void environmentDataSource_parsesPostgresqlUrl() {
        HikariDataSource ds = (HikariDataSource) config.environmentDataSource(
                "postgresql://alice:secret@db.internal:5432/mariageplusdb");

        assertEquals("jdbc:postgresql://db.internal:5432/mariageplusdb", ds.getJdbcUrl());
        assertEquals("alice", ds.getUsername());
        assertEquals("secret", ds.getPassword());
    }

    @Test
    void environmentDataSource_handlesPostgresScheme() {
        HikariDataSource ds = (HikariDataSource) config.environmentDataSource(
                "postgres://bob:p@ss@10.0.0.1:5433/weddings");

        assertEquals("jdbc:postgresql://10.0.0.1:5433/weddings", ds.getJdbcUrl());
        assertEquals("bob", ds.getUsername());
        assertEquals("p@ss", ds.getPassword());
    }

    @Test
    void environmentDataSource_preservesQueryParams() {
        HikariDataSource ds = (HikariDataSource) config.environmentDataSource(
                "postgresql://user:pw@host:5432/db?sslmode=require");

        assertEquals("jdbc:postgresql://host:5432/db?sslmode=require", ds.getJdbcUrl());
    }

    @Test
    void environmentDataSource_usesDefaultDatabaseWhenMissing() {
        HikariDataSource ds = (HikariDataSource) config.environmentDataSource(
                "postgresql://user:pw@host:5432");

        assertEquals("jdbc:postgresql://host:5432/mariageplusdb", ds.getJdbcUrl());
    }
}