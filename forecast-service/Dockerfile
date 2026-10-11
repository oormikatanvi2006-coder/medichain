# forecast-service: build with Maven, run on a small JRE image
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B -DskipTests package

FROM eclipse-temurin:17-jre
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
COPY docker-entrypoint.sh /app/docker-entrypoint.sh
RUN sed -i 's/\r$//' /app/docker-entrypoint.sh && chmod +x /app/docker-entrypoint.sh
ENV PORT=8082 \
    SPRING_H2_CONSOLE_ENABLED=false \
    JAVA_OPTS="-XX:+UseSerialGC -XX:MaxRAMPercentage=60 -Xss512k"
EXPOSE 8082
CMD ["/app/docker-entrypoint.sh"]
