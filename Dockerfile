# Build the application
FROM eclipse-temurin:25-jdk AS build
RUN apt-get update && apt-get install -y maven
COPY . /app
WORKDIR /app
RUN mvn clean package -DskipTests

# Run the application
FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
