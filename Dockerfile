# ── Build stage ──
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -q -DskipTests clean package

# ── Runtime stage ──
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
# Bind to the platform-provided $PORT (Render/Cloud Run set it) or 8080 locally.
ENTRYPOINT ["sh", "-c", "java -Duser.timezone=UTC -jar app.jar --server.port=${PORT:-8080}"]
