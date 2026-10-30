# 贡献指南

感谢你愿意为 Jade Platform 贡献代码。

## 行为准则

请友好、尊重、包容。我们不接受任何形式的骚扰或不当言行。

## 提 Issue

- **Bug 反馈**：先搜索是否已有相同 issue
- **功能建议**：描述清楚场景、痛点、期望方案
- **问题模板**：尽量提供复现步骤、报错信息、环境

## 提 MR

### 流程

1. Fork
2. 创建 feature 分支（`feature/xxx` / `fix/xxx` / `docs/xxx`）
3. 提交代码（commit message 遵循 Conventional Commits）
4. 推到你 fork
5. 提 MR 到 `main`
6. 等 CI 通过 + 至少 1 个 reviewer approve

### Commit 规范

```
feat: 新增用户管理接口
fix: 修复 JWT 解析在 JDK 17 下的兼容问题
docs: 完善 README 排错指南
refactor: 抽出统一响应包装
test: 给 AuthController 加集成测试
chore: 升级 Quarkus 到 3.15.7
```

### 代码规范

**后端**：
- 4 空格缩进，UTF-8
- 公共类必须写 JavaDoc
- 不要在 Service 层直接返回 Entity，用 DTO
- 业务异常用 `BizException`，不要 `RuntimeException`
- Controller 不写业务逻辑，只做参数校验和转发

**前端**：
- 2 空格缩进，无分号
- `<script setup lang="ts">` 必用
- API 调用必须走 Orval 生成的 hooks，不手写 axios
- 组件 props 用 `defineProps<{...}>()` 强类型

### 提 MR 前自检

```bash
make backend-build   # 后端编译通过
make backend-test    # 后端测试通过
cd frontend && npm run type-check   # 前端类型检查
cd frontend && npm run lint         # 前端 lint
```

## 发布

只有 maintainer 能发版：

```bash
# 1. 确认 CHANGELOG.md 已更新
# 2. 打 tag
git tag -a v1.0.0 -m "Release 1.0.0"
git push origin v1.0.0
# 3. GitHub Actions 自动发布到 Maven 仓库
```

## 多数据库迁移约定 (postgresql | mysql | sqlite)

迁移脚本按方言放 `src/main/resources/db/migration/<vendor>/`，三份保持同版本号。以 **PostgreSQL 版为 canonical 先写**，再按对照表派生：

| PostgreSQL (canonical) | MySQL | SQLite |
|---|---|---|
| `BIGSERIAL PRIMARY KEY` | `BIGINT AUTO_INCREMENT PRIMARY KEY` | `INTEGER PRIMARY KEY`（rowid 自增） |
| `TIMESTAMPTZ DEFAULT now()` | `DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6)` | `TEXT DEFAULT CURRENT_TIMESTAMP` |
| `TIMESTAMP DEFAULT NOW()` | 同上 | 同上 |
| `ON CONFLICT (x) DO NOTHING` (seed) | `INSERT IGNORE INTO ...` | 保持 `ON CONFLICT` |
| `INSERT ... SELECT ... WHERE NOT EXISTS` | 加 `FROM DUAL` | 不变 |
| `COMMENT ON TABLE/COLUMN` | 删除（或行内 COMMENT） | 删除 |
| `DROP TABLE ... CASCADE` | 去掉 `CASCADE` | 去掉 `CASCADE` |
| 部分索引 `WHERE deleted=false` | 去掉 WHERE（普通索引） | 保留 |

其他约定：
- Java 原生 SQL 禁用方言语法（底座的两处 `ON CONFLICT` 已改为先查后插）
- 时间统一存 UTC；MySQL 用 `DATETIME(6)`，SQLite 用 TEXT
- CI 需对三库各跑一遍测试矩阵防漂移（PG/MySQL Testcontainers，SQLite 临时文件）
- SQLite 连接串必须带 `date_class=text`（xerial 驱动 Timestamp 存储格式问题）


## MCP 约定 (jade-mcp 基座模块)

- 通用件 (PAT 表/鉴权机制/token API) 在 jade-boot 的 `jade-mcp` 模块; 产品只写 `@Tool` 领域工具
- 认证: 请求头 `X-Mcp-Token` (不用 Authorization —— 底座 smallrye-jwt 会把 Bearer 当 JWT 校验)
- 鉴权必须走 `HttpAuthenticationMechanism` (MCP 端点走 vert.x 路由, ContainerRequestFilter 拦不到)
- 令牌校验含 DB 查询: authenticate 内需 worker 线程 + 手动激活 request context (参考 McpTokenAuthMechanism)
- 无效令牌返回 null (匿名 → 401), 不要抛异常 (JAX-RS 兜底 mapper 会转 500)
- 工具错误: read 类返回错误文本 / 列表类抛 RuntimeException (JSON-RPC -32603)
