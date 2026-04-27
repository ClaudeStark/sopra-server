FROM gradle:9.4.1-jdk17 AS build
WORKDIR /app
COPY gradlew /app/
COPY gradle /app/gradle
RUN chmod +x ./gradlew
COPY build.gradle settings.gradle /app/
COPY src /app/src
RUN ./gradlew clean build --no-daemon

FROM eclipse-temurin:17-jdk
ENV SPRING_PROFILES_ACTIVE=production
RUN groupadd appgroup && \
    useradd -r -g appgroup appuser
USER appuser
WORKDIR /app
COPY --from=build /app/build/libs/*.jar /app/sopra-server.jar
EXPOSE 8080
CMD ["java", "-jar", "/app/sopra-server.jar"]