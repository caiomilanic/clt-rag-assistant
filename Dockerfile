# syntax=docker/dockerfile:1

# Imagens configuráveis caso alguma tag mude: docker compose build --build-arg MAVEN_IMAGE=...
ARG NODE_IMAGE=node:22-alpine
ARG MAVEN_IMAGE=maven:3.9-eclipse-temurin-25
ARG JRE_IMAGE=eclipse-temurin:25-jre

# ---------- interface (React + Vite) ----------
FROM ${NODE_IMAGE} AS web
WORKDIR /build/frontend
COPY frontend/package*.json ./
RUN --mount=type=cache,target=/root/.npm npm install --no-audit --no-fund
COPY frontend/ ./
# O vite.config.ts grava em ../src/main/resources/static, que o Spring Boot serve na mesma porta da API.
RUN npm run build

# ---------- API (Spring Boot) ----------
FROM ${MAVEN_IMAGE} AS build
WORKDIR /build
COPY pom.xml .
COPY src ./src
COPY --from=web /build/src/main/resources/static ./src/main/resources/static
# Os testes rodam no desenvolvimento (mvn test); a imagem só empacota.
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -DskipTests package

# ---------- runtime ----------
FROM ${JRE_IMAGE}
WORKDIR /app
RUN groupadd --system app && useradd --system --gid app --home /app app
COPY --from=build /build/target/clt-rag-assistant-*.jar app.jar
# Caminhos relativos usados pela aplicação (application.yml): corpus, glossário e conjuntos de avaliação.
COPY corpus ./corpus
COPY glossario ./glossario
COPY docs/eval/eval-set.json docs/eval/validacao-set.json ./docs/eval/
USER app
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --start-period=60s --retries=30 \
  CMD curl -fs http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
