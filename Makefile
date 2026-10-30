# =====================================================
# Jade Wiki · 一键命令
# =====================================================
# 用法：make help

SHELL := /bin/zsh
# 强制用 JDK 21（覆盖 jenv 等已设置的 JAVA_HOME）
export JAVA_HOME := /Library/Java/JavaVirtualMachines/zulu-21.jdk/Contents/Home
export PATH := $(JAVA_HOME)/bin:$(PATH)

DOCKER_COMPOSE = docker compose -f docker-compose.wiki.yml
BACKEND_DIR = backend
FRONTEND_DIR = frontend
WIKI = $(BACKEND_DIR)/jade-wiki
# 数据库供应商: postgresql (默认) | mysql | sqlite
DB_VENDOR ?= postgresql

.DEFAULT_GOAL := help

.PHONY: help
help: ## 显示帮助
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "}; {printf "\033[36m%-20s\033[0m %s\n", $$1, $$2}'

# ---------------- 环境 ----------------
.PHONY: env
env: ## 检查环境（JDK / Maven / Node / Docker）
	@echo "JDK:    $$($(JAVA_HOME)/bin/java -version 2>&1 | head -1)"
	@echo "Maven:  $$(./$(BACKEND_DIR)/mvnw -v 2>&1 | head -1)"
	@echo "Node:   $$(node -v)"
	@echo "Docker: $$(docker -v)"

# ---------------- 中间件 ----------------
.PHONY: up down restart logs
up: ## 启动 PG (5434, 无 Redis)
	$(DOCKER_COMPOSE) up -d
	@echo "✅ jade-wiki-postgres 已启动 (5434)"

down: ## 停止中间件
	$(DOCKER_COMPOSE) down
	@echo "🛑 已停止"

restart: down up ## 重启中间件

logs: ## 查看中间件日志
	$(DOCKER_COMPOSE) logs -f

# ---------------- 后端 ----------------
.PHONY: dev backend-build backend-package backend-test backend-clean
dev: up ## 启动后端 dev 模式（含热重载, 8082）
	@cd $(BACKEND_DIR) && JAVA_HOME="$(JAVA_HOME)" PATH="$(JAVA_HOME)/bin:$$PATH" ./mvnw -B quarkus:dev -pl jade-wiki -Dquarkus.analytics.disabled=true

dev-sqlite: ## 启动后端（SQLite 模式, 单文件 DB, 无需 PG）
	@cd $(BACKEND_DIR) && JAVA_HOME="$(JAVA_HOME)" PATH="$(JAVA_HOME)/bin:$$PATH" ./mvnw -B quarkus:dev -pl jade-wiki -Dquarkus.profile=sqlite -Dquarkus.analytics.disabled=true

backend-build: ## 编译后端所有模块
	@cd $(BACKEND_DIR) && JAVA_HOME="$(JAVA_HOME)" ./mvnw -B -DskipTests install

backend-package: ## 打包后端（JVM）
	@cd $(BACKEND_DIR) && JAVA_HOME="$(JAVA_HOME)" ./mvnw -B -DskipTests -pl jade-wiki -am package

backend-test: ## 跑后端测试
	@cd $(BACKEND_DIR) && JAVA_HOME="$(JAVA_HOME)" ./mvnw -B test

backend-clean: ## 清理后端构建产物
	@cd $(BACKEND_DIR) && JAVA_HOME="$(JAVA_HOME)" ./mvnw -B clean

# ---------------- 前端 ----------------
.PHONY: fe-install fe-dev fe-build fe-gen
fe-install: ## 安装前端依赖
	@cd $(FRONTEND_DIR) && npm install

fe-dev: ## 启动前端 dev server (5173)
	@cd $(FRONTEND_DIR) && npm run dev

fe-build: ## 构建前端生产包
	@cd $(FRONTEND_DIR) && npm run build

fe-gen: ## 重新生成 OpenAPI SDK
	@cd $(FRONTEND_DIR) && npm run gen:api

# ---------------- 全栈 ----------------
.PHONY: all-start all-stop
all-start: up ## 启动全栈（后端后台 + 前端后台）
	@echo "🚀 启动后端（用 JDK 21, $(DB_VENDOR) 模式）..."
	@if [ "$(DB_VENDOR)" = "sqlite" ]; then \
		PROFILE="-Dquarkus.profile=sqlite"; \
	else \
		PROFILE=""; \
	fi; \
	cd $(BACKEND_DIR) && JAVA_HOME="$(JAVA_HOME)" PATH="$(JAVA_HOME)/bin:$$PATH" nohup ./mvnw -B quarkus:dev -pl jade-wiki $$PROFILE -Dquarkus.analytics.disabled=true > /tmp/jade-wiki-backend.log 2>&1 &
	@echo "🚀 启动前端..."
	@cd $(FRONTEND_DIR) && nohup npm run dev > /tmp/jade-wiki-frontend.log 2>&1 &
	@sleep 15
	@echo "✅ 全栈已启动（看下面 log 确认）"
	@echo "  后端: http://localhost:8082"
	@echo "  前端: http://localhost:5173"
	@echo ""
	@echo "📋 验证："
	@echo "  tail -f /tmp/jade-wiki-backend.log   # 后端日志"
	@echo "  tail -f /tmp/jade-wiki-frontend.log  # 前端日志"

all-stop: ## 停止全栈
	@pkill -f "quarkus:dev" || true
	@pkill -f "vite" || true
	@echo "🛑 全栈已停止"

# ---------------- 重置 ----------------
.PHONY: reset-db
reset-db: ## 重置数据库（清空 + 重新跑 Flyway）
	@echo "⚠️  即将清空数据库 jade_wiki"
	@docker exec -i jade-wiki-postgres psql -U postgres -c "DROP DATABASE IF EXISTS jade_wiki;"
	@docker exec -i jade-wiki-postgres psql -U postgres -c "CREATE DATABASE jade_wiki;"
	@echo "✅ 数据库已重置，下次启动时 Flyway 会自动建表"

# ---------------- 健康检查 ----------------
.PHONY: health login
health: ## 检查后端健康
	@curl -s http://localhost:8082/q/health | head -10

login: ## 用默认账号登录测试
	@curl -s -X POST http://localhost:8082/api/v1/auth/login \
		-H "Content-Type: application/json" \
		-d '{"username":"admin","password":"admin123"}' | head -3
