# syntax=docker/dockerfile:1

# ----------------------------
# Build stage
# ----------------------------


FROM gradle:8.5-jdk17 AS builder
# Set working directory inside the container
WORKDIR /home/gradle/src

RUN apt-get update && apt-get install -y curl protobuf-compiler

RUN curl -L -o /usr/local/bin/protoc-gen-grpc-java https://repo1.maven.org/maven2/io/grpc/protoc-gen-grpc-java/1.76.0/protoc-gen-grpc-java-1.76.0-linux-x86_64.exe \
    && chmod +x /usr/local/bin/protoc-gen-grpc-java

ENV PATH="/usr/local/bin:$PATH"

# Copy project files and set the correct owner (gradle user inside the image)
COPY --chown=gradle:gradle . .

RUN gradle clean
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
