# syntax=docker/dockerfile:1.6
#
# SkillHub unified Dockerfile.
#
# Builds all three service images (server / web / scanner) from a single file.
# Build context must be the repository root so that the per-service COPY paths
# below resolve correctly.
#
# Build a single service:
#   docker buildx build --target server  -t skillhub.local/server:0.1.0  .
#   docker buildx build --target web     -t skillhub.local/web:0.1.0     .
#   docker buildx build --target scanner -t skillhub.local/scanner:0.1.0 .
#
# Build all three at once:
#   scripts/build-images.sh 0.1.0
#
# Optional build args (forwarded as OCI labels, no functional impact):
#   VERSION=0.1.0
#
# Notes:
# - The legacy per-service Dockerfiles (server/Dockerfile, web/Dockerfile,
#   scanner/Dockerfile) remain intact for backwards compatibility. This file
#   is the canonical single-file path and is what scripts/build-images.sh
#   invokes.
# - All COPY paths use repo-root-relative paths because the build context is
#   the repository root, not a per-service subdirectory.

ARG VERSION=dev

# =========================================================================
# Server (Spring Boot, JDK 21)
# =========================================================================
FROM eclipse-temurin:21-jdk-alpine AS server-build
WORKDIR /build

# Cache Maven dependencies before copying source — same trick used by the
# per-service Dockerfile. Order matches server/pom.xml modules so that any
# local SNAPSHOT resolves without first running `mvn install`.
COPY server/pom.xml ./pom.xml
COPY server/skillhub-domain/pom.xml         ./skillhub-domain/
COPY server/skillhub-auth/pom.xml           ./skillhub-auth/
COPY server/skillhub-search/pom.xml         ./skillhub-search/
COPY server/skillhub-infra/pom.xml          ./skillhub-infra/
COPY server/skillhub-storage/pom.xml        ./skillhub-storage/
COPY server/skillhub-notification/pom.xml   ./skillhub-notification/
COPY server/skillhub-app/pom.xml            ./skillhub-app/
COPY server/mvnw ./mvnw
COPY server/.mvn ./.mvn
RUN chmod +x ./mvnw && ./mvnw -f pom.xml dependency:go-offline -B

COPY server/. .
RUN ./mvnw -f pom.xml package -DskipTests -B

FROM eclipse-temurin:21-jre-alpine AS server
ARG VERSION=dev
LABEL org.opencontainers.image.title="skillhub-server" \
      org.opencontainers.image.version="${VERSION}" \
      org.opencontainers.image.source="https://gitee.com/icarusSH/skillhub"

RUN addgroup -S app && adduser -S app -G app
WORKDIR /app

COPY --from=server-build /build/skillhub-app/target/*.jar app.jar

RUN mkdir -p /var/lib/skillhub/storage && \
    chown -R app:app /app /var/lib/skillhub/storage

USER app

EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --start-period=60s --retries=12 \
    CMD wget -qO- http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]


# =========================================================================
# Web (React 19 + nginx)
# =========================================================================
FROM node:22-alpine AS web-build
WORKDIR /build

RUN corepack enable

# Cache pnpm dependencies before copying source.
COPY web/package.json web/pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile

COPY web/. .
RUN pnpm build

FROM nginx:alpine AS web
ARG VERSION=dev
LABEL org.opencontainers.image.title="skillhub-web" \
      org.opencontainers.image.version="${VERSION}" \
      org.opencontainers.image.source="https://gitee.com/icarusSH/skillhub"

COPY --from=web-build /build/dist /usr/share/nginx/html
COPY --from=web-build /build/src/docs/skill.md.template \
    /usr/share/nginx/html/registry/skill.md.template
COPY web/nginx.conf.template /etc/nginx/templates/default.conf.template
COPY web/runtime-config.js.template /usr/share/nginx/html/runtime-config.js.template
COPY web/docker-entrypoint.d/30-runtime-config.sh /docker-entrypoint.d/30-runtime-config.sh
RUN chmod +x /docker-entrypoint.d/30-runtime-config.sh

EXPOSE 80
HEALTHCHECK --interval=10s --timeout=3s \
    CMD wget -qO- http://127.0.0.1/nginx-health || exit 1


# =========================================================================
# Scanner (Python 3.11, cisco-ai-skill-scanner)
# =========================================================================
# Single-stage: the scanner image is small (~80 MB) and pip needs the system
# toolchain at install time. Keeping build + runtime in one stage avoids
# having to copy site-packages between stages (which is fragile on alpine's
# musl-libc + pip wheel combination).
FROM python:3.11-alpine AS scanner
ARG VERSION=dev
LABEL org.opencontainers.image.title="skillhub-scanner" \
      org.opencontainers.image.version="${VERSION}" \
      org.opencontainers.image.source="https://gitee.com/icarusSH/skillhub"

WORKDIR /app

RUN apk add --no-cache --virtual .build-deps gcc musl-dev libffi-dev && \
    pip install --no-cache-dir cisco-ai-skill-scanner && \
    apk del .build-deps && \
    addgroup -S app && \
    adduser -S app -G app && \
    mkdir -p /tmp/skillhub-scans && \
    chown app:app /tmp/skillhub-scans

USER app

EXPOSE 8000
HEALTHCHECK --interval=10s --timeout=3s \
    CMD wget -qO- http://127.0.0.1:8000/health || exit 1

CMD ["skill-scanner-api", "--host", "0.0.0.0", "--port", "8000"]