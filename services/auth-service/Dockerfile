FROM gradle:9.1.0-jdk21 AS build
WORKDIR /workspace
COPY gradlew gradlew
COPY gradlew.bat gradlew.bat
COPY gradle gradle
COPY settings.gradle.kts build.gradle.kts ./
COPY common ./common
COPY services ./services
RUN chmod +x gradlew
RUN ./gradlew :services:auth-service:bootJar -x test

FROM eclipse-temurin:21-jre
ENV SPRING_OUTPUT_ANSI_ENABLED=ALWAYS \
    JAVA_OPTS=""
WORKDIR /app
COPY --from=build /workspace/services/auth-service/build/libs/auth-service-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8085
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
