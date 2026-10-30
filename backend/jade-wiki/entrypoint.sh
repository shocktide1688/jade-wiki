#!/bin/sh
# All-in-One / API 启动脚本
# 零配置自托管: JWT/Crypto 密钥未提供时自动生成并持久化到数据卷 (/app/data/.env),
# 重启复用同一密钥 (否则已加密数据无法解密)。显式传入的环境变量优先。
set -e

DATA_DIR=/app/data
ENV_FILE=$DATA_DIR/.env

mkdir -p "$DATA_DIR"

if [ -f "$ENV_FILE" ]; then
    . "$ENV_FILE"
fi

gen_key() {
    head -c 32 /dev/urandom | base64 | tr '+/' '-_' | tr -d '='
}

if [ -z "$JADE_JWT_SECRET" ]; then
    JADE_JWT_SECRET="jws_$(gen_key)"
    echo "JADE_JWT_SECRET='$JADE_JWT_SECRET'" >> "$ENV_FILE"
    chmod 600 "$ENV_FILE"
fi

if [ -z "$JADE_CRYPTO_MASTER_KEY" ]; then
    JADE_CRYPTO_MASTER_KEY="$(gen_key)"
    echo "JADE_CRYPTO_MASTER_KEY='$JADE_CRYPTO_MASTER_KEY'" >> "$ENV_FILE"
    chmod 600 "$ENV_FILE"
fi

if [ -z "$JADE_JWT_ISSUER" ]; then
    JADE_JWT_ISSUER="jade-wiki"
    echo "JADE_JWT_ISSUER='$JADE_JWT_ISSUER'" >> "$ENV_FILE"
fi

if [ -z "$JADE_JWT_EXPIRE_SECONDS" ]; then
    JADE_JWT_EXPIRE_SECONDS="7200"
    echo "JADE_JWT_EXPIRE_SECONDS='$JADE_JWT_EXPIRE_SECONDS'" >> "$ENV_FILE"
fi

export JADE_JWT_SECRET JADE_JWT_ISSUER JADE_CRYPTO_MASTER_KEY JADE_JWT_EXPIRE_SECONDS

# MP-JWT 校验 issuer 必须与签发一致
export MP_JWT_VERIFY_ISSUER="$JADE_JWT_ISSUER"

# All-in-One: 后端绑内部回环 (nginx 占对外 8082); API 镜像: 该值被 ENV 覆盖为 0.0.0.0:8082
export QUARKUS_HTTP_HOST="${QUARKUS_HTTP_HOST:-0.0.0.0}"
export QUARKUS_HTTP_PORT="${QUARKUS_HTTP_PORT:-8082}"

if [ -f /etc/nginx/conf.d/app.conf ]; then
    nginx
fi

exec /app/jade-wiki
