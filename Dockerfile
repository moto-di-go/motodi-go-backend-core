FROM eclipse-temurin:21-jre AS runtime

WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# Package on the host first to avoid Maven Central TLS failures inside Docker:
#   ./mvnw -B package -DskipTests
COPY target/motodi-go-backend-core-*.jar app.jar

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "app.jar"]
