# API 서버 이미지. 빌드와 실행을 나눠 실행 이미지에 JDK·Gradle 캐시가 남지 않게 한다.
FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace
COPY . .
RUN ./gradlew :external-api:bootJar -x test --no-daemon

FROM eclipse-temurin:17-jre
COPY --from=build /workspace/external-api/build/libs/*-SNAPSHOT.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
