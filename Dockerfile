# Build the application
FROM maven:3.9.9-eclipse-temurin-25 AS build
COPY . /app
WORKDIR /app
RUN mvn clean package -DskipTests

# Run the application
FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
