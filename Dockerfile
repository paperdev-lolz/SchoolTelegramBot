# Build stage
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# Run stage
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/8MaktabBot-1.0-SNAPSHOT.jar app.jar
COPY data ./data

ENV PORT=10000
EXPOSE 10000

CMD ["java", "-jar", "app.jar"]
