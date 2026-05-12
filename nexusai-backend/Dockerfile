# ─── Stage 1: Build ───────────────────────────────────────────────────────────
# Uses Java 21 so Gradle 8.x / Kotlin 2.0 can parse the JVM version correctly.
# The project toolchain is Java 25 locally; inside Docker we pin to 21 to avoid
# auto-provisioning an internet download.
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /workspace

# Copy Gradle wrapper and build files first (layer-cache friendly)
COPY gradlew .
COPY gradle/ gradle/
COPY settings.gradle.kts .
COPY build.gradle.kts .
COPY gradle.properties .

# Strip the local macOS java.home path — Docker uses the image JDK
RUN chmod +x gradlew && \
    sed -i '/org.gradle.java.home/d' gradle.properties

# Download dependencies (cached layer — runs before source copy)
RUN ./gradlew dependencies --no-daemon --quiet \
      -Dnet.bytebuddy.experimental=true \
      || true

# Copy source and build the JAR (skip tests & native AOT processing)
COPY src/ src/
RUN ./gradlew bootJar --no-daemon \
      -x test \
      -x integrationTest \
      -x processAot \
      -Dnet.bytebuddy.experimental=true

# ─── Stage 2: Runtime ─────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime

LABEL maintainer="Sergio Hernández <sergio@nexusai.local>"
LABEL description="NexusAI Backend — Spring Boot WebFlux (Java 21 container / Java 25 local)"

# Non-root user for security
RUN addgroup -S nexusai && adduser -S nexusai -G nexusai
USER nexusai

WORKDIR /app

# Copy just the built JAR
COPY --from=builder /workspace/build/libs/*.jar app.jar

EXPOSE 8080

ENV JAVA_OPTS="-XX:+UseZGC -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"
ENV SPRING_PROFILES_ACTIVE=prod

HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
