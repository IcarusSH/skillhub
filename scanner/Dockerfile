FROM python:3.11-alpine

# 国内 ECS 经常被 PyPI 官方源卡住, 这里默认走阿里云镜像,
# 海外构建可以 --build-arg PIP_INDEX_URL=https://pypi.org/simple 覆盖回官方源。
ARG PIP_INDEX_URL=https://mirrors.aliyun.com/pypi/simple/

WORKDIR /app

RUN apk add --no-cache --virtual .build-deps gcc musl-dev libffi-dev && \
    pip install --no-cache-dir \
        --index-url "${PIP_INDEX_URL}" \
        --trusted-host mirrors.aliyun.com \
        'pydantic>=2.10,<3' && \
    pip install --no-cache-dir \
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
