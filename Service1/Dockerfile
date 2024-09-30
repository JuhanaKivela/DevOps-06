# Build the application
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY springboot/pom.xml .
COPY springboot/src ./src
RUN mvn clean package -DskipTests

# Create the final image
FROM maven:3.9.9-eclipse-temurin-21
WORKDIR /app
COPY --from=build /app/target/*.jar service1.jar
ENTRYPOINT ["java", "-jar", "/app/service1.jar"]