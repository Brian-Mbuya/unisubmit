FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace

COPY pom.xml mvnw mvnw.cmd ./
COPY .mvn .mvn
RUN mvn -q -B -DskipTests dependency:go-offline

COPY src src
RUN mvn -q -B -DskipTests package

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
# NOTE: no `VOLUME` instruction — Railway rejects it. For persistent uploads,
# attach a Railway Volume mounted at /app/uploads (or a host mount elsewhere).

# Give the app 120 s to boot on first run (Flyway migrations + Hibernate ddl-auto
# against a remote Supabase DB can take 30-60 s on a cold container).
HEALTHCHECK --interval=30s --timeout=10s --start-period=120s --retries=5 \
  CMD ["sh", "-c", "curl -fsS http://127.0.0.1:${PORT}/health || exit 1"]

# Memory budget for Railway's container (512 MB limit on Hobby plan):
#   -Xms64m   — start JVM with a small heap; let it grow as needed
#   -Xmx384m  — hard cap: leaves ~128 MB for the OS + non-heap (Metaspace, threads)
#   -XX:MaxMetaspaceSize=128m — prevent Metaspace from growing unbounded
#   -XX:+UseG1GC — lower pause times and better memory compaction than default GC
#   -Djava.security.egd — faster SecureRandom on Linux (avoids /dev/random blocking)
ENTRYPOINT ["sh", "-c", "java \
  -Xms64m \
  -Xmx384m \
  -XX:MaxMetaspaceSize=128m \
  -XX:+UseG1GC \
  -Djava.security.egd=file:/dev/./urandom \
  -jar /app/unisubmit.jar"]
