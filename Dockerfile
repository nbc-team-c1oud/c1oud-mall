# 빌드 단계
FROM gradle:8.14-jdk21 AS build
WORKDIR /app
COPY . .
RUN gradle build -x test --no-daemon

# 실행 단계
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "-Dspring.profiles.active=prod", "app.jar"]