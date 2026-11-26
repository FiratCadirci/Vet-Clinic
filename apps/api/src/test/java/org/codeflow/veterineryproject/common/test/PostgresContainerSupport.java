package org.codeflow.veterineryproject.common.test;

import org.flywaydb.core.Flyway;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.junit.jupiter.api.condition.EnabledIf;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

/**
 * Gerçek Flyway migration'larıyla kurulan PostgreSQL üzerinde, uygulamanın kullandığı (RLS'e tabi) vet_app rolüyle çalışan testler için taban sınıf.
 * Docker varsa Testcontainers kullanılır. Docker yoksa şu ortam değişkenleriyle harici bir PostgreSQL kullanılabilir:
 * VET_IT_URL (örn. jdbc:postgresql://localhost:5432), VET_IT_ADMIN_USER, VET_IT_ADMIN_PASSWORD, VET_IT_APP_PASSWORD.
 */
@EnabledIf(value = "org.codeflow.veterineryproject.common.test.PostgresContainerSupport#databaseAvailable",
        disabledReason = "Docker veya VET_IT_URL ile harici PostgreSQL gerekli")
public abstract class PostgresContainerSupport {

    private static final String VET_APP_USER = "vet_app";
    private static final String DEFAULT_APP_PASSWORD = "change_me";

    protected static final java.nio.file.Path STORAGE_ROOT = createStorageRoot();

    private static PostgreSQLContainer<?> container;
    private static String jdbcUrl;
    private static String adminUser;
    private static String adminPassword;
    private static String appPassword;
    private static boolean initialized;

    private static java.nio.file.Path createStorageRoot() {
        try {
            java.nio.file.Path dir = java.nio.file.Files.createTempDirectory("vet-it-storage");
            dir.toFile().deleteOnExit();
            return dir;
        } catch (java.io.IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    static boolean databaseAvailable() {
        return System.getenv("VET_IT_URL") != null || DockerClientFactory.instance().isDockerAvailable();
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        initialize();
        registry.add("spring.datasource.url", () -> jdbcUrl);
        registry.add("spring.datasource.username", () -> VET_APP_USER);
        registry.add("spring.datasource.password", () -> appPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.flyway.enabled", () -> "false");
        registry.add("app.storage.local.root", () -> STORAGE_ROOT.toString());
    }

    private static synchronized void initialize() {
        if (initialized) {
            return;
        }
        String externalUrl = System.getenv("VET_IT_URL");
        if (externalUrl != null) {
            adminUser = System.getenv().getOrDefault("VET_IT_ADMIN_USER", "postgres");
            adminPassword = System.getenv().getOrDefault("VET_IT_ADMIN_PASSWORD", "postgres");
            appPassword = System.getenv().getOrDefault("VET_IT_APP_PASSWORD", DEFAULT_APP_PASSWORD);
            String dbName = "vet_it_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
            execute(externalUrl + "/postgres", "CREATE DATABASE " + dbName);
            jdbcUrl = externalUrl + "/" + dbName;
        } else {
            container = new PostgreSQLContainer<>("postgres:15-alpine")
                    .withDatabaseName("Veterinery")
                    .withUsername("postgres")
                    .withPassword("postgres");
            container.start();
            jdbcUrl = container.getJdbcUrl();
            adminUser = container.getUsername();
            adminPassword = container.getPassword();
            appPassword = DEFAULT_APP_PASSWORD;
        }

        createAppRole();
        Flyway.configure()
                .dataSource(jdbcUrl, adminUser, adminPassword)
                .schemas("vet")
                .locations("classpath:db/migration")
                .load()
                .migrate();
        grantAppRole();
        initialized = true;
    }

    private static void createAppRole() {
        String adminDb = jdbcUrl;
        execute(adminDb, "DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '" + VET_APP_USER + "') THEN "
                + "CREATE ROLE " + VET_APP_USER + " LOGIN NOSUPERUSER NOBYPASSRLS NOCREATEDB NOCREATEROLE PASSWORD '" + appPassword + "'; END IF; END $$");
    }

    private static void grantAppRole() {
        execute(jdbcUrl,
                "GRANT USAGE ON SCHEMA vet TO " + VET_APP_USER,
                "GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA vet TO " + VET_APP_USER,
                "GRANT USAGE, SELECT, UPDATE ON ALL SEQUENCES IN SCHEMA vet TO " + VET_APP_USER,
                "GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA vet TO " + VET_APP_USER);
    }

    private static void execute(String url, String... statements) {
        try (Connection connection = DriverManager.getConnection(url, adminUser, adminPassword);
             Statement statement = connection.createStatement()) {
            for (String sql : statements) {
                statement.execute(sql);
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Test veritabanı hazırlanamadı: " + ex.getMessage(), ex);
        }
    }

    protected static DataSource vetAppDataSource() {
        initialize();
        return dataSource(VET_APP_USER, appPassword);
    }

    protected static DataSource superUserDataSource() {
        initialize();
        return dataSource(adminUser, adminPassword);
    }

    protected static DataSource pooledVetAppDataSource(int poolSize) {
        initialize();
        com.zaxxer.hikari.HikariDataSource pool = new com.zaxxer.hikari.HikariDataSource();
        pool.setJdbcUrl(jdbcUrl);
        pool.setUsername(VET_APP_USER);
        pool.setPassword(appPassword);
        pool.setMaximumPoolSize(poolSize);
        pool.setAutoCommit(false);
        return pool;
    }

    private static DataSource dataSource(String user, String password) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.postgresql.Driver");
        dataSource.setUrl(jdbcUrl);
        dataSource.setUsername(user);
        dataSource.setPassword(password);
        return dataSource;
    }
}
