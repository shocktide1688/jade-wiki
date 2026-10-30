# Jade Wiki

> **温润如玉，稳定如石** — 基于 Jade Platform 底座的类 ShowDoc API 文档管理系统。

`jade-wiki` 是从 Jade 底座独立派生的文档产品。保留 Quarkus 3.39、Vue 3.5、JWT/RBAC、多租户、审计、Flyway 等底座能力，业务层实现项目/目录/页面/历史/成员/回收站/搜索等文档管理能力。

## 当前版本 (M1.5 · ShowDoc 功能对齐)

- **项目（item）**：创建/设置/软删，可见性（私有/公开），成员管理（所有者/可编辑/只读）
- **目录树**：无限级目录，上移/下移排序，删除目录页面自动移入"未分类"
- **页面**：Markdown 编辑（vditor 所见即所得），**乐观锁防并发覆盖**（版本冲突返回 4009）
- **历史版本**：每次保存自动快照，保留最近 30 份
- **回收站**：页面软删/恢复
- **搜索**：项目内全文搜索 + 跨项目全局搜索
- **分享（ShowDoc 式）**：公开项目免登录浏览页 `/wiki/view/:itemId`；私密项目凭访问密码换取 token
- **模板**：系统内置（API 接口文档/数据表文档）+ 自建模板，新建页面一键套用
- **附件/图片**：编辑器粘贴/选择上传（白名单后缀，本地磁盘存储），公开项目图片匿名可读
- **导出**：单页 Markdown、项目全量 Markdown zip（按目录组织）
- **复制/排序**：页面一键复制，页面与目录均支持上移/下移
- **开放 API**：项目生成 api_key/api_secret，`POST /api/open/wiki/page/save` 免登录推送文档（CI 友好，按标题存在则覆盖/不存在则创建，catName 自动建目录）
- **多数据库**：PostgreSQL / MySQL / SQLite，同一份代码，启动参数切换
- **无 Redis**：单机部署只需一个数据库
- **回归测试**：`WikiApiTest` @QuarkusTest 覆盖上述全流程

## 快速启动

### 0. 环境要求

JDK 21、Node 22+、Docker（PG 模式需要；SQLite 模式不需要任何外部服务）

### 1. PG 模式（默认，推荐团队使用）

```bash
make up          # 起 jade-wiki-postgres (5434)
make dev         # 后端 dev 模式 (8082)
make fe-install  # 首次
make fe-dev      # 前端 (5173)
```

### 2. SQLite 模式（个人/演示，单文件数据库，零外部依赖）

```bash
mkdir -p backend/jade-wiki/data   # 首次
make dev-sqlite                   # 数据落 backend/jade-wiki/data/jade_wiki.db
make fe-dev
```

### 3. MySQL 模式

```bash
# 自备 MySQL 8 (默认 jdbc:mysql://localhost:3307/jade_wiki, 可用 DB_URL 覆盖)
make dev DB_VENDOR=mysql   # 或: cd backend && ./mvnw quarkus:dev -pl jade-wiki -Dquarkus.profile=mysql
```

访问 http://localhost:5173 ，登录 `admin / admin123`。

### 4. 一键全栈

```bash
make all-start          # PG 模式
make all-start DB_VENDOR=sqlite
make all-stop
```

## 多数据库说明

同一套 Java 代码，通过 `jade.db.vendor` (postgresql | mysql | sqlite) 切换，Flyway 从对应的 `db/migration/<vendor>/` 目录执行方言迁移：

| 供应商 | 启动方式 | 默认连接 |
|---|---|---|
| postgresql | 默认 | `jdbc:postgresql://localhost:5434/jade_wiki` |
| mysql | `-Dquarkus.profile=mysql` | `jdbc:mysql://localhost:3307/jade_wiki` |
| sqlite | `-Dquarkus.profile=sqlite` | `jdbc:sqlite:./data/jade_wiki.db` (WAL) |

新迁移约定：先写 PG 版（canonical），再按对照表派生 MySQL/SQLite 版，三份保持同版本号。三库差异对照（`BIGSERIAL`/`AUTO_INCREMENT`/`INTEGER PRIMARY KEY`、`TIMESTAMPTZ`/`DATETIME(6)`/`TEXT`、`ON CONFLICT`/`INSERT IGNORE`）见 CONTRIBUTING。

SQLite 注意：连接串带 `date_class=text`（xerial 驱动默认把 Timestamp 存成毫秒整数，会导致 Hibernate 读不回来），且需先建 `data/` 目录。

## 端口规划

| 服务 | 端口 | 说明 |
|---|---|---|
| jade-wiki 后端 | 8082 | 底座 8080/8081、jade-ldap 8080 错开 |
| jade-wiki-postgres | 5434 | 底座 5432、jade-ldap 5433 错开 |
| 前端 | 5173 | |

## API 一览 (M1)

```
POST   /api/v1/auth/login                    登录（底座）
GET    /api/v1/wiki/items                    我的项目（分页/搜索）
POST   /api/v1/wiki/items                    创建项目
PUT    /api/v1/wiki/items/{id}               更新项目（所有者）
DELETE /api/v1/wiki/items/{id}               删除项目（软删）
GET    /api/v1/wiki/items/{id}/members       成员列表
POST   /api/v1/wiki/items/{id}/members       添加成员
PUT    /api/v1/wiki/items/{id}/members/{mid} 修改成员角色
DELETE /api/v1/wiki/items/{id}/members/{mid} 移除成员
GET    /api/v1/wiki/catalogs?itemId=         目录树
POST   /api/v1/wiki/catalogs                 新建目录
PUT    /api/v1/wiki/catalogs/{id}            重命名目录
DELETE /api/v1/wiki/catalogs/{id}            删除目录
GET    /api/v1/wiki/pages?itemId=&catId=&keyword=&trash=   页面列表/搜索/回收站
POST   /api/v1/wiki/pages                    新建页面
GET    /api/v1/wiki/pages/{id}               页面详情
PUT    /api/v1/wiki/pages/{id}               保存页面（乐观锁，冲突 4009）
DELETE /api/v1/wiki/pages/{id}               移入回收站
POST   /api/v1/wiki/pages/{id}/restore       恢复页面
GET    /api/v1/wiki/pages/{id}/history       历史版本列表
GET    /api/v1/wiki/pages/history-detail/{hid} 历史版本详情
POST   /api/v1/wiki/pages/{id}/copy          复制页面
PUT    /api/v1/wiki/pages/{id}/move          页面上移/下移
GET    /api/v1/wiki/pages/global-search      全局搜索（跨项目）
GET    /api/v1/wiki/pages/{id}/export        导出单页 Markdown
GET    /api/v1/wiki/items/{id}/export        导出项目 zip
GET    /api/v1/wiki/templates                模板列表
POST   /api/v1/wiki/templates                新建模板
DELETE /api/v1/wiki/templates/{id}           删除模板
POST   /api/v1/wiki/attachments              上传附件/图片
GET    /api/v1/wiki/attachments/{id}/download 下载附件
GET    /api/v1/wiki/items/{id}/api-keys      开放 API Key 列表
POST   /api/v1/wiki/items/{id}/api-keys      生成 API Key
DELETE /api/v1/wiki/items/{id}/api-keys/{kid} 删除 API Key
POST   /api/v1/wiki/share/verify             分享密码校验(公开接口)
GET    /api/v1/wiki/view/**                  匿名浏览(公开接口)
POST   /api/open/wiki/page/save              开放API推送文档(免登录)
```

OpenAPI: http://localhost:8082/q/openapi · Swagger UI: http://localhost:8082/q/swagger-ui

## MCP 接入 (AI 助手读文档)

个人中心 → 「MCP 访问令牌」生成 PAT (仅显示一次), 然后配置 AI 客户端:

```bash
# Claude Code / ZCode
claude mcp add --transport http jade-wiki http://localhost:8082/mcp \
  --header "X-Mcp-Token: <你的PAT>"

# Claude Desktop / Cursor (mcp.json)
{ "mcpServers": { "jade-wiki": {
    "url": "http://localhost:8082/mcp",
    "headers": { "X-Mcp-Token": "<你的PAT>" } } } }
```

可用工具 (只读, 权限与令牌所属账号一致):

| 工具 | 说明 |
|---|---|
| `list_projects` | 列出可见项目 |
| `get_project_tree` | 项目目录树 + 页面清单 |
| `search_pages` | 标题+正文全文搜索 |
| `read_page` | 读取页面 Markdown |

MCP 服务端为基座通用模块 `jade-mcp` (jade-boot), 各产品 (wiki/ldap/商城) 只需挂领域 @Tool。

## 路线图

- **M2**：导出 word/离线 HTML、单页项目(item_type=single)、附件管理页、回收站彻底删除/定期清理
- **M3**：管理端（项目总览/审计）、**jade-ldap 接入**（LDAP bind 登录 + 用户组同步）、开放 API HMAC 签名加固、MCP 写路径评估
- **M4**：RunAPI 式接口调试、Grafana 仪表板

## 架构

```
jade-wiki/
├── backend/                  # Quarkus 多模块（fork 自 jade-boot）
│   ├── jade-wiki/            # 唯一 runnable：entity/service/api + db/migration/<vendor>/
│   ├── jade-admin/           # 底座 RBAC/登录/系统管理（已去 Redis 依赖）
│   └── jade-common/web/security/log/oss/codegen/dependencies
├── frontend/                 # Vue 3.5 + Element Plus（pages/wiki/ + vditor）
├── docker-compose.wiki.yml   # 仅 PG(5434)，无 Redis
└── Makefile                  # make help
```

权限模型：项目所有者(0) > 可编辑成员(1) > 只读成员(2) > 非成员(-1)，admin 角色绕过。登录、菜单、操作日志复用底座。

## 底座说明

Jade 是面向企业级 Java 应用的全栈开发底座（Quarkus 3.39 LTS + Vue 3.5，GraalVM 原生镜像 84ms 启动），详见 jade-boot 仓库。
