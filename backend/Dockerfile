# Multi-stage production build for Raghu NSS Platform backend
FROM gradle:8.14-jdk21-alpine AS build
WORKDIR /workspace

# Copy build configuration and gradle wrapper
COPY build.gradle settings.gradle ./
COPY gradle ./gradle
COPY gradlew ./
COPY gradlew.bat ./

# Copy source and build production artifact
COPY src ./src
RUN gradle bootJar -x test --no-daemon

# Minimal, secure production runtime
FROM eclipse-temurin:21-jre-alpine

# Run as non-privileged service user
RUN addgroup -S nssgroup && adduser -S nssuser -G nssgroup

WORKDIR /app
COPY --from=build --chown=nssuser:nssgroup /workspace/build/libs/*.jar app.jar

USER nssuser

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/./urandom"
ENV PORT=8080

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
