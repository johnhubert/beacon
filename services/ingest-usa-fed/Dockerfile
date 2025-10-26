FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Build the shared data model so generated protobuf classes are available.
COPY common/data-model/pom.xml common/data-model/pom.xml
COPY common/data-model/src common/data-model/src
RUN mvn -q -e -f common/data-model/pom.xml clean install -DskipTests

# Prime dependencies for the ingestion service.
COPY services/ingest-usa-fed/pom.xml services/ingest-usa-fed/pom.xml
RUN mvn -q -e -f services/ingest-usa-fed/pom.xml dependency:go-offline

# Build the ingestion service.
COPY services/ingest-usa-fed/src services/ingest-usa-fed/src
RUN mvn -q -e -f services/ingest-usa-fed/pom.xml clean package -DskipTests

FROM eclipse-temurin:21-jre
ENV SPRING_OUTPUT_ANSI_ENABLED=ALWAYS \
    JAVA_OPTS=""
WORKDIR /app
COPY --from=build /workspace/services/ingest-usa-fed/target/ingest-usa-fed-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8090
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
