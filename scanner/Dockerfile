# syntax=docker/dockerfile:1.6
FROM python:3.11-alpine

# 国内 ECS 经常被 PyPI 官方源 + Alpine 官方仓库卡住,这里默认全部走阿里云镜像。
# 海外构建可以 --build-arg 覆盖回官方源。
ARG APK_REPO=https://mirrors.aliyun.com/alpine/v3.21/main
ARG APK_REPO_COMMUNITY=https://mirrors.aliyun.com/alpine/v3.21/community
ARG PIP_INDEX_URL=https://mirrors.aliyun.com/pypi/simple/

# 切换 Alpine apk 仓库到阿里云镜像(覆盖默认的 dl-cdn.alpinelinux.org)
RUN sed -i "s|dl-cdn.alpinelinux.org|mirrors.aliyun.com|g" /etc/apk/repositories

WORKDIR /app

# buildkit 缓存挂载:apk 包缓存 + pip wheel 缓存都跨 build 复用。
# 第二次 build 直接命中本地缓存,只下增量。
# 注意:cache mount 不会进入最终镜像层,image 大小不会变大。
RUN --mount=type=cache,target=/var/cache/apk,sharing=locked \
    --mount=type=cache,target=/etc/apk/cache,sharing=locked \
    --mount=type=cache,target=/root/.cache/pip,sharing=locked \
    apk add --no-cache --virtual .build-deps gcc musl-dev libffi-dev && \
    pip install \
        --index-url "${PIP_INDEX_URL}" \
        --trusted-host mirrors.aliyun.com \
        'pydantic>=2.10,<3' && \
    pip install \
        --index-url "${PIP_INDEX_URL}" \
        --trusted-host mirrors.aliyun.com \
        cisco-ai-skill-scanner && \
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