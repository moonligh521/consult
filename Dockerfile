FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
COPY consult-framework/pom.xml consult-framework/pom.xml
COPY consult-framework/consult-framework-common/pom.xml consult-framework/consult-framework-common/pom.xml
COPY consult-registration-agent/pom.xml consult-registration-agent/pom.xml
COPY consult-registration-agent/consult-registration-agent-api/pom.xml consult-registration-agent/consult-registration-agent-api/pom.xml
COPY consult-registration-agent/consult-registration-agent-service/pom.xml consult-registration-agent/consult-registration-agent-service/pom.xml
COPY consult-framework consult-framework
COPY consult-registration-agent consult-registration-agent
RUN mvn -B -q -pl consult-registration-agent/consult-registration-agent-service -am package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/consult-registration-agent/consult-registration-agent-service/target/consult-registration-agent-service-0.1.0-SNAPSHOT.jar app.jar
EXPOSE 18081
ENTRYPOINT ["java", "-jar", "app.jar"]
