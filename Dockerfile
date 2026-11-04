# syntax=docker/dockerfile:1
# ---- build ----
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY src src
RUN ./mvnw -B -q -DskipTests package && cp target/connectone-*.jar app.jar

# ---- run ----
FROM eclipse-temurin:25-jre
RUN useradd --system --uid 10001 connectone
WORKDIR /app
COPY --from=build /workspace/app.jar app.jar
USER connectone
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -Duser.timezone=UTC" \
    CONNECTONE_PORT=18080
EXPOSE 18080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
