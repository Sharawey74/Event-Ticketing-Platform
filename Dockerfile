# =========================================================
# Stage 1: Build — full JDK required for Maven compilation
# =========================================================
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /app

# Copy Maven wrapper and POM first — maximizes layer caching.
# If only src/ changes, Docker reuses the dependency download layer.
COPY .mvn/ .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw
RUN ./mvnw dependency:go-offline -q

# Copy source after dependencies are cached
COPY src ./src
RUN ./mvnw clean package -DskipTests -q

# =========================================================
# Stage 2: Runtime — JRE only, minimal footprint
# =========================================================
FROM eclipse-temurin:21-jre
WORKDIR /app

# curl is needed for the docker-compose healthcheck below — not present
# in the base JRE image by default.
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# Non-root user — never run production containers as root
RUN useradd -m appuser && chown -R appuser /app
USER appuser

# Copy only the JAR from the build stage — nothing else transfers
COPY --from=builder /app/target/*.jar app.jar

# Matches server.port in application.yaml (8088)
EXPOSE 8088

# -XX:MaxRAMPercentage caps heap as a percentage of the container's memory
# limit rather than the host's total RAM. No explicit container memory
# limit is set in docker-compose.yml, so cgroup detection would otherwise
# fall back to host-visible memory and the JVM's conservative 25% default
# would undersize the heap for a container running only this one process.
# -Djava.security.egd speeds up startup by using /dev/urandom instead of
# the blocking /dev/random entropy source.
ENTRYPOINT ["java", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
