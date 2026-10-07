FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY gradlew gradlew.bat settings.gradle build.gradle ./
COPY gradle ./gradle
COPY src ./src
RUN bash gradlew --no-daemon bootJar

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN groupadd --system beautyconnect && useradd --system --gid beautyconnect beautyconnect
COPY --from=build /workspace/build/libs/beautyconnect-0.1.0-SNAPSHOT.jar /app/app.jar
ENV SPRING_PROFILES_ACTIVE=prod
USER beautyconnect
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
