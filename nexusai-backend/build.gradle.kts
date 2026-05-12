import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    java
    id("org.springframework.boot") version "3.4.5"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.graalvm.buildtools.native") version "0.10.6"
}

group = "com.sahernandezz.nexusai"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

// ─── Integration Test Source Set ───────────────────────────────────────────────
val integrationTest: SourceSet by sourceSets.creating {
    compileClasspath += sourceSets.main.get().output + sourceSets.test.get().output
    runtimeClasspath += sourceSets.main.get().output + sourceSets.test.get().output
}

val integrationTestImplementation: Configuration by configurations.getting {
    extendsFrom(configurations.testImplementation.get())
}

val integrationTestRuntimeOnly: Configuration by configurations.getting {
    extendsFrom(configurations.testRuntimeOnly.get())
}
// ──────────────────────────────────────────────────────────────────────────────

configurations {
    compileOnly { extendsFrom(configurations.annotationProcessor.get()) }
}

repositories {
    mavenCentral()
    maven { url = uri("https://repo.spring.io/milestone") }
}

extra["springAiVersion"] = "1.0.0"
extra["testcontainersVersion"] = "1.21.4"

dependencyManagement {
    imports {
        mavenBom("org.springframework.ai:spring-ai-bom:${property("springAiVersion")}")
        mavenBom("org.testcontainers:testcontainers-bom:${property("testcontainersVersion")}")
    }
}

dependencies {
    // ─── Core WebFlux ──────────────────────────────────────────────────────────
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // ─── Reactive Data ─────────────────────────────────────────────────────────
    implementation("org.springframework.boot:spring-boot-starter-data-r2dbc")
    implementation("org.postgresql:r2dbc-postgresql")
    // JDBC driver only for Flyway migrations (not used at runtime by the app)
    runtimeOnly("org.postgresql:postgresql")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("org.springframework.boot:spring-boot-starter-jdbc") // Flyway needs a DataSource

    // ─── Redis ─────────────────────────────────────────────────────────────────
    implementation("org.springframework.boot:spring-boot-starter-data-redis-reactive")

    // ─── Messaging ─────────────────────────────────────────────────────────────
    implementation("org.springframework.boot:spring-boot-starter-amqp")
    implementation("io.projectreactor.rabbitmq:reactor-rabbitmq:1.5.6")

    // ─── Spring AI ─────────────────────────────────────────────────────────────
    implementation("org.springframework.ai:spring-ai-starter-model-openai")
    implementation("org.springframework.ai:spring-ai-starter-vector-store-pgvector")
    implementation("org.springframework.ai:spring-ai-starter-model-chat-memory-repository-jdbc")
    implementation("org.springframework.ai:spring-ai-starter-mcp-server-webflux")
    implementation("org.springframework.ai:spring-ai-starter-mcp-client-webflux")
    // RAG pipeline
    implementation("org.springframework.ai:spring-ai-rag")
    implementation("org.springframework.ai:spring-ai-advisors-vector-store")
    // M3 — Document readers
    implementation("org.springframework.ai:spring-ai-pdf-document-reader")
    implementation("org.springframework.ai:spring-ai-tika-document-reader")

    // ─── JWT ───────────────────────────────────────────────────────────────────
    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")

    // ─── API Docs ──────────────────────────────────────────────────────────────
    implementation("org.springdoc:springdoc-openapi-starter-webflux-ui:2.8.6")

    // ─── Observability ─────────────────────────────────────────────────────────
    implementation("io.micrometer:micrometer-registry-prometheus")
    implementation("io.micrometer:micrometer-tracing-bridge-otel")
    implementation("io.opentelemetry:opentelemetry-exporter-otlp")

    // ─── MinIO (Object Storage) ────────────────────────────────────────────────
    implementation("io.minio:minio:8.5.17")

    // ─── Utilities ─────────────────────────────────────────────────────────────
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    // ─── Unit Tests ────────────────────────────────────────────────────────────
    testImplementation("org.springframework.boot:spring-boot-starter-test") {
        exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
    }
    testImplementation("io.projectreactor:reactor-test")
    testImplementation("org.springframework.security:spring-security-test")

    // ─── Integration Tests ─────────────────────────────────────────────────────
    integrationTestImplementation("org.testcontainers:testcontainers")
    integrationTestImplementation("org.testcontainers:junit-jupiter")
    integrationTestImplementation("org.testcontainers:postgresql")
    integrationTestImplementation("org.testcontainers:rabbitmq")
    integrationTestImplementation("com.redis:testcontainers-redis:2.2.2")
    // Spring Boot test starter pulls in WebTestClient + JUnit 5 + Mockito (used
    // by IntegrationTestConfig to stub the Spring AI beans).
    integrationTestImplementation("org.springframework.boot:spring-boot-starter-test")
}

// ─── Tasks ─────────────────────────────────────────────────────────────────────

// Handle duplicate files in bootJar (common with Gradle 9.0 and Spring AI native-image metadata)
tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    // Byte Buddy (Mockito) experimental mode needed until it officially supports Java 25
    jvmArgs("-Dnet.bytebuddy.experimental=true")
    testLogging {
        events(TestLogEvent.PASSED, TestLogEvent.FAILED, TestLogEvent.SKIPPED)
        exceptionFormat = TestExceptionFormat.FULL
        showCauses = true
        showExceptions = true
        showStackTraces = true
    }
}

/**
 * Resolves the active Docker daemon socket so Testcontainers works on hosts
 * that don't expose the default `/var/run/docker.sock` (Colima, OrbStack,
 * Rancher Desktop, podman, etc.). Falls back to whatever Testcontainers can
 * auto-detect when the lookup fails.
 */
val resolvedDockerHost: String? = run {
    val fromEnv = providers.environmentVariable("DOCKER_HOST").orNull
    if (!fromEnv.isNullOrBlank()) return@run fromEnv
    try {
        val out = providers.exec {
            commandLine("sh", "-c",
                    "docker context inspect \"$(docker context show)\" -f '{{.Endpoints.docker.Host}}' 2>/dev/null")
        }.standardOutput.asText.get().trim()
        out.takeIf { it.isNotEmpty() }
    } catch (_: Exception) { null }
}

val integrationTestTask = tasks.register<Test>("integrationTest") {
    description = "Runs integration tests against live containers (Testcontainers)."
    group = "verification"
    testClassesDirs = sourceSets["integrationTest"].output.classesDirs
    classpath = sourceSets["integrationTest"].runtimeClasspath
    useJUnitPlatform()
    shouldRunAfter(tasks.test)

    systemProperty("spring.profiles.active", "test")

    // Hand the active Docker socket to Testcontainers via env. Without this,
    // Testcontainers searches a fixed list of well-known socket paths and
    // gives up on non-default installs (OrbStack, Colima, Rancher) — making
    // every IT class fail to initialize before JUnit can even apply
    // `@Testcontainers(disabledWithoutDocker = true)`.
    resolvedDockerHost?.let { environment("DOCKER_HOST", it) }

    // Pin a modern Docker Engine API version. The docker-java client shipped
    // with Testcontainers defaults to 1.32 which OrbStack and recent Docker
    // Desktop reject ("client version is too old, minimum supported is 1.40").
    // docker-java reads this via the DOCKER_API_VERSION environment variable.
    environment("DOCKER_API_VERSION", "1.43")

    // Provide a stub OpenAI key so Spring AI's auto-configured embedding /
    // image / chat beans can instantiate. The integration tests don't issue
    // real LLM calls — only Postgres, Rabbit and Redis interactions.
    environment("OPENAI_API_KEY", "test-stub-key-not-used")

    testLogging {
        events(TestLogEvent.PASSED, TestLogEvent.FAILED, TestLogEvent.SKIPPED)
        exceptionFormat = TestExceptionFormat.FULL
    }
}

// Don't gate the regular `check` lifecycle on integration tests — they need
// Docker. Run them explicitly with `./gradlew integrationTest` (or in CI).
// Keeping `check` lean means `./gradlew build` works on any developer machine.

// Ensure integrationTest compiles after test
tasks.named("compileIntegrationTestJava") {
    dependsOn(tasks.testClasses)
}

// ─── GraalVM Native Image ───────────────────────────────────────────────────
graalvmNative {
    // Let the toolchain plugin find GraalVM from PATH or GRAALVM_HOME
    toolchainDetection = false

    binaries {
        named("main") {
            imageName = "nexusai-backend"
            mainClass = "com.sahernandezz.nexusai.NexusAiApplication"
            buildArgs.addAll(
                "--no-fallback",                          // fail if native not possible
                "-H:+ReportExceptionStackTraces",
                "-H:+AddAllCharsets",
                "--enable-url-protocols=http,https",
                "--install-exit-handlers",
                "-march=native",                          // optimise for build machine
                // Reactor / Netty need run-time init
                "--initialize-at-run-time=" +
                    "io.netty.channel.DefaultFileRegion," +
                    "io.netty.channel.epoll.Epoll," +
                    "io.netty.channel.kqueue.KQueue," +
                    "io.netty.handler.ssl.OpenSsl," +
                    "io.netty.internal.tcnative.SSL",
                // Logback / SLF4J can be built-time
                "--initialize-at-build-time=" +
                    "ch.qos.logback," +
                    "org.slf4j.LoggerFactory," +
                    "org.slf4j.helpers"
            )
        }

        named("test") {
            buildArgs.addAll("--no-fallback", "-H:+AddAllCharsets")
        }
    }

    // Generate Graalvm reachability metadata for third-party libs during tests
    metadataRepository {
        enabled = true
    }
}
