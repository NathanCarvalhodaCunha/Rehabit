FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q dependency:go-offline
COPY src ./src
RUN mvn -q package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/rehabit-api-1.0.0.jar app.jar
# Roda com um usuário sem privilégio: uma falha na API não vira root no
# container. Ele só escreve em /app (fotos e banco H2, quando usados).
RUN useradd --system --uid 10001 --no-create-home rehabit && chown -R rehabit /app
USER rehabit
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
