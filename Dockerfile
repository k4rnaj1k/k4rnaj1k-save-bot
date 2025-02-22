# ------------------------------
# Stage 1: Build with Maven
# ------------------------------
FROM maven:3.9.8-eclipse-temurin-22-alpine AS build

# Set working directory
WORKDIR /app

# Copy the Maven configuration and download dependencies
COPY pom.xml ./
RUN mvn dependency:go-offline -B

# Copy the source code and build the Spring Boot application
COPY src ./src
RUN mvn clean package -DskipTests

# ------------------------------
# Stage 2: Download yt-dlp and its dependencies
# ------------------------------
FROM alpine:3.18 AS ytdlp

# Download the latest yt-dlp binary and make it executable
RUN wget -O /usr/local/bin/yt-dlp "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp" && \
    chmod +x /usr/local/bin/yt-dlp

# ------------------------------
# Stage 3: Minimal runtime image with Python
# ------------------------------
FROM eclipse-temurin:22-jre-alpine

# Set working directory
WORKDIR /app

# Install Python (required by yt-dlp) in the runtime stage
RUN apk add --no-cache python3

# Install wget and additional dependencies like ffmpeg
RUN apk add --no-cache wget ffmpeg

# Copy the built Spring Boot JAR from the build stage
COPY --from=build /app/target/*.jar app.jar

# Copy the yt-dlp binary from the ytdlp stage
COPY --from=ytdlp /usr/local/bin/yt-dlp /usr/local/bin/yt-dlp

# Run the Spring Boot application
CMD ["java", "-jar", "app.jar"]
