FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
COPY src ./src
RUN mvn -B -q package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/target/registration-assistant-0.1.0-SNAPSHOT.jar app.jar
EXPOSE 18081
ENTRYPOINT ["java", "-jar", "app.jar"]
