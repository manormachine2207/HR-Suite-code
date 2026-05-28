# syntax=docker/dockerfile:1

# --- Build stage ---
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
# Cache deps: copy parent + module poms first, then go-offline.
COPY pom.xml ./pom.xml
COPY application/pom.xml ./application/pom.xml
# Reactor root is the aggregator ./pom.xml; -pl selects the module, -am also-makes the parent.
RUN --mount=type=cache,target=/root/.m2 \
    mvn -ntp -pl application -am dependency:go-offline || true
COPY application/src ./application/src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -ntp -pl application -am package -DskipTests
RUN cp application/target/hr-suite-application-*.jar /build/app.jar

# --- Runtime stage ---
FROM eclipse-temurin:21-jre-alpine AS runtime
RUN addgroup -S app && adduser -S -G app app
WORKDIR /app
COPY --from=build /build/app.jar /app/app.jar
USER app
EXPOSE 8080
# Actuator health endpoint is public (SecurityConfig).
HEALTHCHECK --interval=10s --timeout=3s --start-period=40s --retries=5 \
  CMD wget -qO- http://127.0.0.1:8080/actuator/health | grep -q '"status":"UP"' || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
