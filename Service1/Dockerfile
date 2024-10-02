# Build the application
FROM maven:3.9.9-eclipse-temurin-21-alpine AS build
WORKDIR /app
COPY springboot/pom.xml .
COPY springboot/src ./src
RUN mvn clean package -DskipTests

# Create the final image
FROM maven:3.9.9-eclipse-temurin-21-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar service1.jar
ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "/app/service1.jar"]