FROM eclipse-temurin:21-jdk-alpine-3.23 AS build

WORKDIR /workspace

COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw --batch-mode --no-transfer-progress dependency:go-offline

COPY src src
RUN ./mvnw --batch-mode --no-transfer-progress clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine-3.23 AS runtime

WORKDIR /app

RUN addgroup -S rmm && adduser -S rmm -G rmm

COPY --from=build --chown=rmm:rmm /workspace/target/*.jar application.jar

USER rmm

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/application.jar"]

