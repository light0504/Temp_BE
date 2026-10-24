FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src src
RUN --mount=type=cache,target=/root/.m2 mvn -B -DskipTests package
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 10001 fbus && mkdir /app/data && chown -R fbus /app
COPY --from=build /app/target/fbus-api-1.0.0.jar app.jar
USER fbus
EXPOSE 8081
ENTRYPOINT ["java","-jar","app.jar"]
