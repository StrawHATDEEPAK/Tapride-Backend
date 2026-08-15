# NOTE: build context for this Dockerfile is the REPO ROOT, not order-service/,
# because order-service/pom.xml now inherits from the root parent POM (../pom.xml).
# See docker-compose.yml: build.context = "." and build.dockerfile = "order-service/Dockerfile"

# --- Build stage ---
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Copy parent POM first for better Docker layer caching (rarely changes)
COPY pom.xml .
COPY order-service/pom.xml order-service/pom.xml
RUN mvn -B -f order-service/pom.xml dependency:go-offline

COPY order-service/src order-service/src
RUN mvn -B -f order-service/pom.xml clean package -DskipTests

# --- Runtime stage ---
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S tapride && adduser -S tapride -G tapride
COPY --from=build /app/order-service/target/order-service.jar app.jar
USER tapride

EXPOSE 8080
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-jar", "app.jar"]

HEALTHCHECK --interval=10s --timeout=3s --start-period=30s --retries=5 \
  CMD wget -qO- http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1
