# syntax=docker/dockerfile:1

# ----------------------------
# Build stage
# ----------------------------
FROM gradle:8.5-jdk17-alpine AS builder

# Set working directory inside the container
WORKDIR /home/gradle/src

# Copy project files and set the correct owner (gradle user inside the image)
COPY --chown=gradle:gradle . .

# Build the project without running tests to speed up the image build
RUN gradle build -x test

# ----------------------------
# Runtime stage
# ----------------------------
FROM eclipse-temurin:17-jre-alpine AS runtime

# Create a non-root user for running the application
RUN addgroup -S twins && adduser -S twins -G twins

WORKDIR /app

# Copy the executable JAR from the builder stage
COPY --from=builder /home/gradle/src/build/libs/*.jar app.jar

# Ensure the non-root user owns the application JAR
RUN chown twins:twins /app/app.jar
USER twins

# Expose gRPC port
EXPOSE 6565

# Allow JVM options to be passed in at runtime via JAVA_OPTS
ENV JAVA_OPTS=""

# Default command
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
