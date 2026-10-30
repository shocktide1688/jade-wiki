FROM node:24-alpine AS builder
WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

# nginx 官方镜像: /etc/nginx/templates/*.template 自动 envsubst 后启用
# BACKEND_URL 环境变量指定后端地址 (docker compose: http://api:8082)
FROM nginx:1.27-alpine
COPY --from=builder /app/dist/ /usr/share/nginx/html/
COPY nginx-templates/ /etc/nginx/templates/
ENV BACKEND_URL=http://127.0.0.1:8082
EXPOSE 80

HEALTHCHECK --interval=10s --timeout=3s --retries=5 \
    CMD wget -qO- http://localhost/ || exit 1
