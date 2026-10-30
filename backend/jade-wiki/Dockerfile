# =====================================================
# Jade Wiki · 多阶段构建（JVM 版）
# =====================================================
# 用法：
#   docker build -f backend/jade-wiki/Dockerfile -t jade-wiki:1.0 backend/
#   docker run -p 8082:8082 -e DB_URL=... jade-wiki:1.0

# ============ Stage 1: 构建 ============
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /build

# 先拷贝所有模块的 pom（最大化依赖层缓存）
COPY pom.xml ./
COPY jade-dependencies/pom.xml jade-dependencies/
COPY jade-common/pom.xml jade-common/
COPY jade-web/pom.xml jade-web/
COPY jade-security/pom.xml jade-security/
COPY jade-log/pom.xml jade-log/
COPY jade-oss/pom.xml jade-oss/
COPY jade-codegen/pom.xml jade-codegen/
COPY jade-admin/pom.xml jade-admin/
COPY jade-mcp/pom.xml jade-mcp/
COPY jade-mail/pom.xml jade-mail/
COPY jade-ldap-client/pom.xml jade-ldap-client/
COPY jade-wiki/pom.xml jade-wiki/

RUN mvn -B -q dependency:go-offline || true

# 拷贝源码并构建 (无 Redis / 无 demo 模块)
COPY . .
RUN mvn -B -DskipTests -pl jade-wiki -am package

# ============ Stage 2: 运行时 ============
FROM eclipse-temurin:21-jre
WORKDIR /app

RUN groupadd -r jade && useradd -r -g jade jade \
    && mkdir -p /app/data /app/logs && chown -R jade:jade /app

COPY --from=builder /build/jade-wiki/target/quarkus-app/lib/      /app/lib/
COPY --from=builder /build/jade-wiki/target/quarkus-app/*.jar     /app/
COPY --from=builder /build/jade-wiki/target/quarkus-app/app/      /app/app/
COPY --from=builder /build/jade-wiki/target/quarkus-app/quarkus/  /app/quarkus/

USER jade

# SQLite 模式数据落 /app/data (挂卷持久化)
VOLUME ["/app/data"]

EXPOSE 8082

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Dfile.encoding=UTF-8"
ENV JAVA_APP_JAR="/app/quarkus-run.jar"

ENTRYPOINT [ "sh", "-c", "exec java $JAVA_OPTS -jar $JAVA_APP_JAR" ]

HEALTHCHECK --interval=10s --timeout=3s --retries=5 \
    CMD wget -qO- http://localhost:8082/q/health/live || exit 1
