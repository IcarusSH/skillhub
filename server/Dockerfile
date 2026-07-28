# ---- Build Stage ----
# syntax=docker/dockerfile:1.6
FROM eclipse-temurin:21-jdk-alpine AS build

# 国内 ECS 从 Maven Central 拉依赖常常失败,这里把镜像写进容器,build 一次过。
# 海外构建可以 --build-arg MAVEN_MIRROR=https://repo.maven.apache.org/maven2 覆盖回官方。
ARG MAVEN_MIRROR=https://maven.aliyun.com/repository/public

WORKDIR /app

# 把镜像源注入 ~/.m2/settings.xml,这样 ./mvnw 走的也是镜像。
RUN mkdir -p /root/.m2 && \
    printf '<settings><mirrors><mirror><id>aliyun</id><url>%s</url><mirrorOf>*</mirrorOf></mirror></mirrors></settings>' \
        "${MAVEN_MIRROR}" > /root/.m2/settings.xml

# Cache dependencies
COPY pom.xml .
COPY skillhub-domain/pom.xml skillhub-domain/
COPY skillhub-auth/pom.xml skillhub-auth/
COPY skillhub-search/pom.xml skillhub-search/
COPY skillhub-infra/pom.xml skillhub-infra/
COPY skillhub-storage/pom.xml skillhub-storage/
COPY skillhub-notification/pom.xml skillhub-notification/
COPY skillhub-app/pom.xml skillhub-app/
COPY mvnw .
COPY .mvn .mvn

# buildkit 缓存挂载:Maven 本地仓库 ~/.m2/repository 跨 build 复用,
# 第二次 build 增量几十秒而不是几分钟。
RUN --mount=type=cache,target=/root/.m2/repository,sharing=locked \
    ./mvnw dependency:go-offline -B

COPY . .
RUN --mount=type=cache,target=/root/.m2/repository,sharing=locked \
    ./mvnw package -DskipTests -B

# ---- Runtime Stage ----
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app

COPY --from=build /app/skillhub-app/target/*.jar app.jar

# Create storage directory and set permissions
RUN mkdir -p /var/lib/skillhub/storage && \
    chown -R app:app /app /var/lib/skillhub/storage

USER app

EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --start-period=60s --retries=12 \
  CMD wget -qO- http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
