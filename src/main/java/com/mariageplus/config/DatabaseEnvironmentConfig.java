package com.mariageplus.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

/**
 * Configuration de la source de données pour les plateformes PaaS (Railway,
 * Heroku, Google Cloud, Oracle Cloud, etc.).
 *
 * <p>Ces plateformes ne fournissent PAS de variable `SPRING_DATASOURCE_URL` au
 * format JDBC, mais une variable unique `DATABASE_URL` au format
 * `scheme://user:password@host:port/database`. Ce bean la détecte, extrait les
 * identifiants et construit une URL JDBC PostgreSQL valide.</p>
 *
 * <p>Si `DATABASE_URL` est absente, rien n'est surchargé : on retombe sur les
 * propriétés `spring.datasource.*` habituelles (local/Docker).</p>
 */
@Configuration
public class DatabaseEnvironmentConfig {

    @Bean
    @Primary
    @ConditionalOnProperty(name = "DATABASE_URL")
    public DataSource environmentDataSource(
            @Value("${DATABASE_URL:}") String databaseUrl) {
        ConnectionInfo info = parse(databaseUrl);
        String jdbcUrl = "jdbc:postgresql://" + info.hostPort + "/" + info.database
                + (info.query == null || info.query.isEmpty() ? "" : "?" + info.query);

        return DataSourceBuilder.create()
                .url(jdbcUrl)
                .username(info.username)
                .password(info.password)
                .driverClassName("org.postgresql.Driver")
                .build();
    }

    private static ConnectionInfo parse(String raw) {
        String body = raw.trim();
        // Enlève le schéma (postgres:// , postgresql:// , etc.)
        body = body.replaceFirst("^[a-zA-Z][a-zA-Z0-9+.-]*://", "");

        String auth = null;
        String rest = body;
        int at = body.lastIndexOf('@');
        if (at >= 0) {
            auth = body.substring(0, at);
            rest = body.substring(at + 1);
        }

        String username = null;
        String password = null;
        if (auth != null) {
            int colon = auth.indexOf(':');
            if (colon >= 0) {
                username = auth.substring(0, colon);
                password = auth.substring(colon + 1);
            } else {
                username = auth;
            }
        }

        String query = null;
        int q = rest.indexOf('?');
        if (q >= 0) {
            query = rest.substring(q + 1);
            rest = rest.substring(0, q);
        }

        String hostPort = rest;
        String database = null;
        int slash = rest.indexOf('/');
        if (slash >= 0) {
            hostPort = rest.substring(0, slash);
            database = rest.substring(slash + 1);
        }

        if (database == null || database.isEmpty()) {
            database = "mariageplusdb";
        }

        return new ConnectionInfo(
                htmlDecode(username),
                htmlDecode(password),
                htmlDecode(hostPort),
                database,
                query
        );
    }

    /**
     * Les plateformes peuvent encoder certains caractères de l'identifiant.
     * Nettoie les résidus d'encodage HTML/X-www-form-urlencoded simples.
     */
    private static String htmlDecode(String value) {
        if (value == null) {
            return null;
        }
        return value
                .replace("&#39;", "'")
                .replace("&amp;", "&")
                .replace("&quot;", "\"");
    }

    /** Petite structure intermédiaire pour transporter les valeurs parsées. */
    private record ConnectionInfo(String username, String password,
                                  String hostPort, String database, String query) {
    }
}