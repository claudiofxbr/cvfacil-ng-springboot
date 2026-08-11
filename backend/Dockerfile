# syntax=docker/dockerfile:1

# ---- build ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
# Cache de dependências: só reexecuta se pom.xml mudar.
# Desativa o profile de binários do Postgres embarcado (Linux) — só é usado no
# profile "local" (dev), não é necessário para empacotar o backend de produção.
RUN mvn -B -P '!embedded-pg-linux' dependency:go-offline
COPY src ./src
RUN mvn -B -P '!embedded-pg-linux' -DskipTests package

# ---- runtime ----
FROM eclipse-temurin:21-jre-jammy AS runtime
WORKDIR /app
RUN useradd --system --uid 1001 --create-home cvfacil
COPY --from=build /app/target/cvfacil-backend-*.jar app.jar
USER cvfacil

EXPOSE 8080

# Tuning de JVM conforme especificado no PRD (seção 5.3).
ENTRYPOINT ["java", "-Xms512m", "-Xmx2g", "-XX:+UseG1GC", "-jar", "app.jar"]
