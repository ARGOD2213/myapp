# syntax=docker/dockerfile:1
# One Dockerfile for every Java service:  docker build --build-arg SERVICE=core-service --build-arg PORT=18081 -t connectone/core-service .
ARG SERVICE=core-service
ARG PORT=18081

FROM eclipse-temurin:25-jdk AS build
ARG SERVICE
WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw pom.xml ./
# poms first: dependency layer is cached until a pom changes
COPY services/core-service/pom.xml services/core-service/pom.xml
COPY services/notification-service/pom.xml services/notification-service/pom.xml
COPY services/api-gateway/pom.xml services/api-gateway/pom.xml
RUN chmod +x mvnw && ./mvnw -B -q -pl services/${SERVICE} -am dependency:go-offline
COPY services/${SERVICE}/src services/${SERVICE}/src
RUN ./mvnw -B -q -DskipTests -pl services/${SERVICE} -am package && cp services/${SERVICE}/target/${SERVICE}-*.jar /workspace/app.jar

FROM eclipse-temurin:25-jre
ARG PORT
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/* && useradd --system --uid 10001 connectone
WORKDIR /app
COPY --from=build /workspace/app.jar app.jar
USER connectone
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -Duser.timezone=UTC"
EXPOSE ${PORT}
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
