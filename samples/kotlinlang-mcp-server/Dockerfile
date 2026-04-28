FROM gradle:jdk21-alpine AS build
WORKDIR /project
COPY gradle/ gradle/
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY src/ src/
RUN ./gradlew shadowJar --no-daemon

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
WORKDIR /app
COPY --from=build /project/build/libs/*-all.jar app.jar
USER appuser
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
