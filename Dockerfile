FROM maven:3.9.16-eclipse-temurin-21-noble@sha256:613124833fa6718ded9d655a2ebfab6425818c178f899116b93560b6f1c9ffe9 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline
COPY src ./src
RUN mvn -B -ntp clean package -DskipTests

FROM eclipse-temurin:21-jre-noble@sha256:981e055f0f1d1518a0e7307840f22247e55d91fe000f4b0f5bd01681d79ed126
RUN useradd --system --uid 10001 payflow
WORKDIR /app
COPY --from=build /workspace/target/payflow-*.jar app.jar
USER payflow
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
