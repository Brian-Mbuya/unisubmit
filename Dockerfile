FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace

COPY pom.xml mvnw mvnw.cmd ./
COPY .mvn .mvn
RUN ./mvnw -q -DskipTests dependency:go-offline

COPY src src
RUN ./mvnw -q -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app

RUN apt-get update \
  && apt-get install -y --no-install-recommends curl \
  && rm -rf /var/lib/apt/lists/*

RUN mkdir -p /app/uploads
COPY --from=build /workspace/target/unisubmit-0.0.1-SNAPSHOT.jar /app/unisubmit.jar

ENV PORT=8080
ENV APP_UPLOAD_DIR=/app/uploads

EXPOSE 8080
VOLUME ["/app/uploads"]

HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
  CMD ["sh", "-c", "curl -fsS http://127.0.0.1:${PORT}/health || exit 1"]

ENTRYPOINT ["sh", "-c", "java -jar /app/unisubmit.jar"]
