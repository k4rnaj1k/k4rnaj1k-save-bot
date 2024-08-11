# Use the official Maven image as the base image
FROM maven:3.8.7-openjdk-17-slim AS build

# Set the working directory inside the container
WORKDIR /app

# Copy the pom.xml and other Maven configuration files first
# This helps in leveraging Docker's layer caching
COPY pom.xml ./

COPY src ./src

CMD ["mvn", "clean", "spring-boot:run"]