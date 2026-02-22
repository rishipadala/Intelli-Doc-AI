# ============================================================
# IntelliDoc AI - Spring Boot Backend Dockerfile
# Multi-stage build for optimized image size
# ============================================================

# Stage 1: Build the application
FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /app

# Copy Maven wrapper and pom.xml first (for dependency caching)
COPY mvnw pom.xml ./
COPY .mvn .mvn

# Make Maven wrapper executable
RUN chmod +x mvnw

# Download dependencies (cached layer unless pom.xml changes)
RUN ./mvnw dependency:go-offline -B

# Copy source code
COPY src ./src

# Build the application (skip tests for faster builds)
RUN ./mvnw package -DskipTests -B

# Stage 2: Create the runtime image
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Install git (required by JGit for cloning repos)
RUN apk add --no-cache git

# Create directory for cloning repos
RUN mkdir -p /tmp/repos

# Copy the built JAR from the build stage
COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

# JVM memory optimization for 512MB container (Koyeb free tier)
ENTRYPOINT ["java", "-Xmx400m", "-Xms200m", "-jar", "app.jar", "--spring.profiles.active=prod"]
