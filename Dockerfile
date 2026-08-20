FROM gradle:9.6.1-jdk21 AS build

WORKDIR /workspace
COPY gradle gradle
COPY gradlew gradlew.bat settings.gradle.kts build.gradle.kts gradle.properties ./
RUN ./gradlew --no-daemon dependencies
COPY src src
RUN ./gradlew --no-daemon clean bootJar

FROM eclipse-temurin:21-jre

RUN useradd --system --uid 10001 --create-home panfu
WORKDIR /app
COPY --from=build /workspace/build/libs/panfu-game-server-*.jar app.jar
USER 10001
EXPOSE 9595 9596
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
