# 三个服务共用一个 Dockerfile，用 MODULE 选要打的模块：
#   docker build -t consult-agent    --build-arg MODULE=consult-registration-agent/consult-registration-agent-service .
#   docker build -t consult-identity --build-arg MODULE=consult-identity/consult-identity-service .
#   docker build -t consult-gateway  --build-arg MODULE=consult-gateway .
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
ARG MODULE=consult-registration-agent/consult-registration-agent-service
COPY . .
RUN mvn -B -q -pl ${MODULE} -am package -DskipTests \
    && cp ${MODULE}/target/*-SNAPSHOT.jar /app.jar

FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 app
WORKDIR /app
COPY --from=build /app.jar app.jar
USER 10001
# 堆按容器内存比例给；OOM 直接退出让 K8s 重建，不在半死状态继续接流量
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["java", "-jar", "app.jar"]
