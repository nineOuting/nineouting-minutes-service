# syntax=docker/dockerfile:1
#
# 공통 모듈(nineouting-common)은 GitHub Packages에서 받으므로 빌드할 때 토큰이 필요하다.
# BuildKit secret으로 넣어 이미지 레이어에 남지 않게 한다:
#   docker build --secret id=gpr_actor,env=GITHUB_ACTOR --secret id=gpr_token,env=GPR_TOKEN -t minutes-service .

# --- 빌드 ---
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
COPY src src

RUN --mount=type=secret,id=gpr_actor --mount=type=secret,id=gpr_token \
    chmod +x gradlew && \
    GITHUB_ACTOR="$(cat /run/secrets/gpr_actor)" \
    GPR_TOKEN="$(cat /run/secrets/gpr_token)" \
    ./gradlew bootJar --no-daemon -x test

# --- 실행 ---
FROM eclipse-temurin:21-jre AS run
WORKDIR /app

RUN groupadd --system app && useradd --system --gid app --no-create-home app

COPY --from=build /workspace/build/libs/*.jar app.jar

USER app
EXPOSE 8084

HEALTHCHECK --interval=10s --timeout=5s --start-period=30s --retries=10 \
    CMD wget -q --spider http://localhost:8084/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
