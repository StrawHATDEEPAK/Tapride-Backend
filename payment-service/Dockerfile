# NOTE: build context for this Dockerfile is the REPO ROOT (see docker-compose.yml),
# because payment-service/pom.xml inherits from the root parent POM.

# --- Build stage ---
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

COPY pom.xml .
COPY payment-service/pom.xml payment-service/pom.xml
RUN mvn -B -f payment-service/pom.xml dependency:go-offline

COPY payment-service/src payment-service/src
RUN mvn -B -f payment-service/pom.xml clean package -DskipTests

# --- Runtime stage ---
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S tapride && adduser -S tapride -G tapride
COPY --from=build /app/payment-service/target/payment-service.jar app.jar
USER tapride

EXPOSE 8080
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-jar", "app.jar"]

HEALTHCHECK --interval=10s --timeout=3s --start-period=30s --retries=5 \
  CMD wget -qO- http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1
