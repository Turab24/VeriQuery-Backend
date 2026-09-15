# syntax=docker/dockerfile:1

# ---------------------------------------------------------------------------
# Stage 1 - build
# Dependencies are resolved in their own layer so a source-only change does not
# re-download the whole dependency tree on every rebuild.
# ---------------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /build

COPY pom.xml ./
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q clean package -DskipTests

# ---------------------------------------------------------------------------
# Stage 2 - runtime
# JRE only, non-root, no build tooling in the shipped image.
# ---------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-jammy

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system --gid 1001 spring \
    && useradd --system --uid 1001 --gid spring --create-home spring \
    && mkdir -p /app/data/documents \
    && chown -R spring:spring /app

WORKDIR /app

COPY --from=build --chown=spring:spring /build/target/*.jar /app/app.jar

USER spring

EXPOSE 8080

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseContainerSupport -Djava.security.egd=file:/dev/./urandom" \
    SPRING_PROFILES_ACTIVE=prod \
    STORAGE_LOCATION=/app/data/documents

HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 \
    CMD ["sh", "-c", "curl -fsS http://localhost:8080/actuator/health/readiness || exit 1"]

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
