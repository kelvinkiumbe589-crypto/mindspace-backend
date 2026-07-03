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
# App Runner / ECS route traffic to this port (Spring Boot default 8080).
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
