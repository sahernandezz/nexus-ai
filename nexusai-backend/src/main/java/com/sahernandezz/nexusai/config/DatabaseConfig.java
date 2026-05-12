package com.sahernandezz.nexusai.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * Explicit JDBC DataSource + JdbcTemplate configuration.
 *
 * <p><b>Why this class exists:</b>
 * Spring Boot's {@code DataSourceAutoConfiguration} carries the condition
 * {@code @ConditionalOnMissingBean(type = "io.r2dbc.spi.ConnectionFactory")}.
 * Because this project uses Spring Data R2DBC for reactive persistence, a
 * {@code ConnectionFactory} bean is always present and {@code DataSourceAutoConfiguration}
 * is silently skipped — leaving neither {@code DataSource} nor {@code JdbcTemplate}
 * in the application context.
 *
 * <p>Both Flyway (schema migrations) and Spring AI PgVectorStore (vector embeddings)
 * require a blocking JDBC {@code DataSource} / {@code JdbcTemplate}. This
 * configuration provides them directly, bypassing the broken conditional.
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
public class DatabaseConfig {

    @Value("${spring.datasource.url}")
    private String url;

    @Value("${spring.datasource.username}")
    private String username;

    @Value("${spring.datasource.password}")
    private String password;

    @Value("${spring.datasource.hikari.maximum-pool-size:5}")
    private int maxPoolSize;

    /**
     * Blocking JDBC DataSource used by Flyway and PgVectorStore.
     * Marked {@code @Primary} so it wins over any auto-configured candidate.
     */
    @Primary
    @Bean("jdbcDataSource")
    public DataSource jdbcDataSource() {
        log.info("[DatabaseConfig] Creating JDBC DataSource | url={}", url);
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(maxPoolSize);
        config.setConnectionTimeout(30_000);
        // -1 = don't fail on startup if DB isn't immediately reachable
        config.setInitializationFailTimeout(-1);
        config.setPoolName("nexusai-jdbc");
        return new HikariDataSource(config);
    }

    /** JdbcTemplate required by PgVectorStore and Spring AI chat-memory. */
    @Bean
    public JdbcTemplate jdbcTemplate(DataSource jdbcDataSource) {
        return new JdbcTemplate(jdbcDataSource);
    }
}

