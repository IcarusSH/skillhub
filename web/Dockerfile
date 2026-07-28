# syntax=docker/dockerfile:1.6
FROM node:22-alpine AS build
RUN corepack enable

# 国内 ECS 拉 npm 慢,默认走 npmmirror,海外构建可以 --build-arg NPM_REGISTRY 覆盖。
ARG NPM_REGISTRY=https://registry.npmmirror.com

# 把 Alpine apk 仓库也切到阿里云(虽然 node:alpine 自带 node 不用装别的,
# 但 pnpm 偶尔会调 apk 装 python/make 等)
RUN sed -i 's|dl-cdn.alpinelinux.org|mirrors.aliyun.com|g' /etc/apk/repositories

WORKDIR /app
COPY package.json pnpm-lock.yaml ./
RUN pnpm config set registry "${NPM_REGISTRY}"

# buildkit 缓存挂载:pnpm store 跨 build 复用。
# pnpm 把所有下载过的包缓存在 ~/.local/share/pnpm/store/v3,
# 第二次 build 时增量只有 lock 文件的差量。
RUN --mount=type=cache,target=/root/.local/share/pnpm/store,sharing=locked \
    pnpm install --frozen-lockfile

COPY . .
RUN pnpm build

FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY --from=build /app/src/docs/skill.md.template /usr/share/nginx/html/registry/skill.md.template
COPY nginx.conf.template /etc/nginx/templates/default.conf.template
COPY runtime-config.js.template /usr/share/nginx/html/runtime-config.js.template
COPY docker-entrypoint.d/30-runtime-config.sh /docker-entrypoint.d/30-runtime-config.sh
RUN chmod +x /docker-entrypoint.d/30-runtime-config.sh
EXPOSE 80
HEALTHCHECK --interval=10s --timeout=3s \
  CMD wget -qO- http://127.0.0.1/nginx-health || exit 1
