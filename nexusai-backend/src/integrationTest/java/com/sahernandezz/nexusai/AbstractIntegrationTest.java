package com.sahernandezz.nexusai;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

/**
 * Base class for integration tests.
 *
 * <p>The whole class — and any subclass — is automatically <b>skipped</b>
 * when no Docker daemon is reachable, thanks to
 * {@code @Testcontainers(disabledWithoutDocker = true)}. This avoids the
 * {@code NoClassDefFoundError} cascade that happened previously when the
 * containers were started inside a {@code static {}} block at class-load time
 * — that pattern crashes the JVM-level class initialization before JUnit
 * has a chance to apply any @EnabledIf condition.
 *
 * <p>Containers are declared {@code static} + {@code @Container} so the
 * Testcontainers extension reuses them across every test method in the class,
 * starting them lazily only after the Docker availability check passes.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
@Import(IntegrationTestConfig.class)
public abstract class AbstractIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("pgvector/pgvector:pg16")
                    .asCompatibleSubstituteFor("postgres"))
                    .withDatabaseName("nexusai_test")
                    .withUsername("nexusai")
                    .withPassword("nexusai");

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(6379);

    @Container
    static final RabbitMQContainer RABBITMQ =
            new RabbitMQContainer(DockerImageName.parse("rabbitmq:3-management"));

    @Container
    static final GenericContainer<?> MINIO =
            new GenericContainer<>(DockerImageName.parse("minio/minio:latest"))
                    .withExposedPorts(9000)
                    .withEnv("MINIO_ROOT_USER", "minioadmin")
                    .withEnv("MINIO_ROOT_PASSWORD", "minioadmin")
                    .withCommand("server /data");

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        // R2DBC
        registry.add("spring.r2dbc.url", () ->
                "r2dbc:postgresql://" + POSTGRES.getHost() + ":" + POSTGRES.getMappedPort(5432)
                + "/" + POSTGRES.getDatabaseName());
        registry.add("spring.r2dbc.username", POSTGRES::getUsername);
        registry.add("spring.r2dbc.password", POSTGRES::getPassword);

        // JDBC / Flyway
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);

        // Redis
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379).toString());

        // RabbitMQ
        registry.add("spring.rabbitmq.host", RABBITMQ::getHost);
        registry.add("spring.rabbitmq.port", () -> RABBITMQ.getMappedPort(5672).toString());
        registry.add("spring.rabbitmq.username", RABBITMQ::getAdminUsername);
        registry.add("spring.rabbitmq.password", RABBITMQ::getAdminPassword);

        // MinIO
        registry.add("minio.endpoint", () ->
                "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000));
        registry.add("minio.access-key", () -> "minioadmin");
        registry.add("minio.secret-key", () -> "minioadmin");
        registry.add("minio.bucket", () -> "nexusai-test-bucket");
    }

    @LocalServerPort
    protected int port;

    protected WebTestClient webTestClient() {
        return WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .responseTimeout(Duration.ofSeconds(15))
                .build();
    }
}
