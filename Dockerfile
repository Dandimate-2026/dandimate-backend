# 두루두루 백엔드 이미지. 빌드: docker build -t durooduroo-backend .
# 실행에 필요한 환경변수는 .env.example과 README "운영 배포"를 본다.

FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN ./gradlew --no-daemon dependencies > /dev/null
COPY src ./src
RUN ./gradlew --no-daemon bootJar -x test

FROM eclipse-temurin:21-jre
# ffprobe: webm 등 음성 길이 측정 / heif-convert: HEIC 사진 JPEG 파생본
RUN apt-get update \
    && apt-get install -y --no-install-recommends ffmpeg libheif-examples curl \
    && rm -rf /var/lib/apt/lists/*
RUN useradd --system --uid 10001 --create-home duroo
WORKDIR /app
COPY --from=build /workspace/build/libs/durooduroo-backend-*.jar /app/app.jar
USER duroo
ENV SPRING_PROFILES_ACTIVE=mysql \
    JAVA_OPTS="-XX:MaxRAMPercentage=75"
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s CMD curl -fs http://localhost:8080/api/v1/health || exit 1
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
