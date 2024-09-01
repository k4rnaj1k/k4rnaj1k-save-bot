# Use the official Maven image as the base image
FROM maven:3.9.8-eclipse-temurin-22-alpine AS build

# Set the working directory inside the container
WORKDIR /app

# Copy the pom.xml and other Maven configuration files first
# This helps in leveraging Docker's layer caching
COPY pom.xml ./

COPY src ./src
COPY scripts ./scripts
RUN apk add --no-cache python3 py3-virtualenv
RUN python3 -m venv scripts/venv
RUN scripts/venv/bin/pip install beautifulsoup4 Pillow requests
RUN mkdir result temp
ENV PYTHON_PATH=/app/scripts/venv/bin/python3
CMD ["mvn", "clean", "spring-boot:run"]
