# Multi-stage production build for Raghu NSS Platform backend
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace

# Copy build configuration and gradle wrapper
COPY gradlew ./
COPY gradle ./gradle
COPY build.gradle settings.gradle ./
RUN chmod +x ./gradlew

# Copy source and build production artifact
COPY src ./src
RUN ./gradlew bootJar -x test --no-daemon

# Minimal, secure production runtime
FROM eclipse-temurin:21-jre-alpine

# Run as non-privileged service user
RUN addgroup -S nssgroup && adduser -S nssuser -G nssgroup

WORKDIR /app
COPY --from=build --chown=nssuser:nssgroup /workspace/build/libs/*.jar app.jar

USER nssuser

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/./urandom -XX:TieredStopAtLevel=1"

EXPOSE 10000 8080

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -Dserver.port=${PORT:-10000} -jar /app/app.jar"]

