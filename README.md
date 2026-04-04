# Pet

宠物社区与 AI 助手一体化项目。

当前仓库采用多模块结构，包含：

- `frontend`：前端应用，负责页面、路由、用户交互
- `backend`：Spring Boot 后端，负责鉴权、宠物管理、帖子、上传、AI 会话编排入口
- `AIService`：独立 Python AI 服务，负责 Question Rewrite、Tool 路由、结构化回答与可选 RAG
- `deploy`：部署配置、compose 文件、镜像构建脚本
- `docs`：项目文档

## 1. 项目能力

当前主要能力包括：

- 用户注册、登录与 JWT 鉴权
- 宠物信息管理
- 宠物体重记录与趋势分析
- 社区帖子、评论、点赞
- 图片上传与前端直读访问
- AI 助手多轮对话
- AI Tool 调用
  - `weight_analysis`
  - `location_search`
- 本地版本化 RAG 能力（代码已支持）

## 2. 目录结构

```text

├─ backend
├─ frontend
├─ AIService
├─ deploy
├─ docs
├─ uploads
└─ AIlog
```

说明：

- `uploads` 为运行时上传目录，不是源码目录
- `AIlog` 为 AI 调试日志目录，不是源码目录

## 3. 模块说明

### 3.1 backend

技术栈：

- Java 21
- Spring Boot 3
- MySQL
- Redis
- Flyway

职责：

- 提供业务 API
- 统一鉴权与异常处理
- 管理 AI 会话、消息持久化
- 调用 `AIService` 获取 AI 回答

### 3.2 frontend

技术栈：

- Vue 3
- Vue Router
- Vite

职责：

- 提供宠物社区前端页面
- 调用后端 API
- 展示 AI 助手对话界面

### 3.3 AIService

技术栈：

- Python 3.12
- FastAPI
- Redis
- DashScope / Qwen
- 可选本地 RAG

职责：

- Question Rewrite
- Tool 决策
- Tool 调用
- 最终结构化回答生成
- 可选知识库检索

## 4. AI 链路说明

当前 AI 调用链路为：

1. 前端调用后端 AI 接口
2. 后端记录会话和消息
3. 后端调用 `AIService`
4. `AIService` 执行 Rewrite / Decision / Tool / Final Response
5. 后端保存结构化结果并返回前端

## 5. 本地开发

### 5.1 backend

```bash
cd backend
mvn spring-boot:run
```

### 5.2 frontend

```bash
cd frontend
pnpm install
pnpm dev
```

### 5.3 AIService

```bash
cd AIService
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
python run.py
```

## 6. 部署入口

部署相关文件位于 `deploy/`：

- `docker-compose.mysql.yml`
- `docker-compose.redis.yml`
- `docker-compose.aiservice.yml`
- `docker-compose.backend.yml`
- `docker-compose.frontend.yml`
- `build-export-image.ps1`

镜像构建脚本：

- [build-export-image.ps1]

生产部署说明：

- [production-deploy-guide.md]

## 7. 当前线上说明

当前服务器内存资源较小，`AIService` 的本地 RAG 相关依赖与模型初始化会明显增加内存占用。

因此当前线上部署策略是：

- `AIService` 正常启用
- AI 基础对话、Rewrite、Tool 路由正常启用
- **RAG 未在服务器上默认开启**

当前原因：

- 服务器内存资源不足
- 开启 RAG 后容易触发 OOM，导致容器被系统杀掉

当前线上建议配置：

- `RAG_ENABLED=false`

补充说明：

- RAG 代码能力仍然保留在仓库中
- 后续若服务器升级内存，或将 RAG 拆分到独立高内存服务，可重新启用
- 当前代码已支持在服务启动后通过管理接口手动加载 RAG

## 8. 后续建议

如果后续计划正式启用 RAG，建议至少满足以下条件之一：

- 提升服务器内存配置
- 为 RAG 单独部署高内存服务
- 将 embedding / RAG 依赖拆分出主 AIService 镜像

## 9. 说明

本 README 面向仓库总览与部署理解。

更细的模块级说明可继续查看：

- [AIService/README.md]
- [docs/production-deploy-guide.md]
