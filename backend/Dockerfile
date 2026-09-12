# Stage 1: Build the Spring Boot application
# Using 'jammy' instead of 'focal' which fully supports Java 21
FROM eclipse-temurin:21-jdk-jammy AS builder

# Set the working directory inside the container
WORKDIR /app

# Copy the Maven wrapper and its directory
COPY mvnw .
COPY .mvn .mvn

# Copy the build configuration file
COPY pom.xml .

# Copy the source code
COPY src src

# Make the Maven wrapper script executable
RUN chmod +x mvnw

# Build the Spring Boot application
RUN ./mvnw clean package -DskipTests

# Stage 2: Create the final, lightweight runtime image
FROM eclipse-temurin:21-jre-jammy

# Set the working directory inside the container
WORKDIR /app

# Copy the executable JAR from the builder stage
COPY --from=builder /app/target/backend-0.0.1-SNAPSHOT.jar app.jar

# Expose the port
EXPOSE 8080

# Define the command to run your application
ENTRYPOINT ["java", "-jar", "app.jar"]