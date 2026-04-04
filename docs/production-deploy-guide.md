# 生产部署说明（当前版本）

本文面向当前仓库的正式发布版本，适用于以下部署结构：

- `frontend`、`backend`、`AIService`、`MySQL`、`Redis` 五个独立容器
- 使用 `deploy/` 中的五个独立 compose 文件分服务部署
- 前端 Nginx 直接读取宿主机上传目录
- 后端通过 HTTP 调用独立的 AIService
- AIService 同时持久化日志目录和本地 RAG 数据目录

## 1. 当前部署文件结构

`deploy/` 目录中的关键文件如下：

- `docker-compose.mysql.yml`
- `docker-compose.redis.yml`
- `docker-compose.aiservice.yml`
- `docker-compose.backend.yml`
- `docker-compose.frontend.yml`
- `.env`
- `.env.example`
- `env/mysql.env`
- `env/aiservice.env`
- `env/backend.env`
- `env/frontend.env`
- `config/backend/application.yml`
- `config/frontend/nginx.conf`
- `build-export-image.ps1`

## 2. 服务拆分与更新原则

服务拆分：

- MySQL：`docker-compose.mysql.yml`
- Redis：`docker-compose.redis.yml`
- AIService：`docker-compose.aiservice.yml`
- Backend：`docker-compose.backend.yml`
- Frontend：`docker-compose.frontend.yml`

更新原则：

- 更新 `frontend`、`backend`、`AIService` 时，不重启 MySQL / Redis
- 发布时优先只更新受影响的服务
- AIService 如果涉及 Prompt、Tool 或 RAG 变更，需要同时核对 `deploy/env/aiservice.env`

## 3. 首次部署准备

### 3.1 创建宿主机目录

```bash
sudo mkdir -p /srv/pet/mysql/data
sudo mkdir -p /srv/pet/redis/data
sudo mkdir -p /srv/pet/uploads
sudo mkdir -p /srv/pet/AIlog
sudo mkdir -p /srv/pet/aiservice/data
sudo mkdir -p /srv/pet/deploy
```

说明：

- `/srv/pet/uploads`：后端写入，前端只读
- `/srv/pet/AIlog`：AIService 日志
- `/srv/pet/aiservice/data`：本地 RAG 知识文件、FAISS 索引、`active_kb.json`

### 3.2 创建共享 docker 网络（只需一次）

```bash
docker network create pet-network
```

### 3.3 上传 deploy 目录到服务器

建议放在：

- `/srv/pet/deploy`

## 4. 关键配置说明

### 4.1 统一变量（`deploy/.env`）

重点项：

- `AISERVICE_IMAGE`
- `BACKEND_IMAGE`
- `FRONTEND_IMAGE`
- `MYSQL_DATA_DIR`
- `REDIS_DATA_DIR`
- `UPLOADS_DIR`
- `AI_LOG_DIR`
- `RAG_DATA_DIR`
- `BACKEND_UPSTREAM`

建议每次发版只改镜像 tag，其余路径类变量保持稳定。

当前推荐在这里直接维护镜像地址，例如：

- `AISERVICE_IMAGE=crpi-xxx-vpc.cn-beijing.personal.cr.aliyuncs.com/gj_pet/pet-aiservice:v0.2.1`
- `BACKEND_IMAGE=crpi-xxx-vpc.cn-beijing.personal.cr.aliyuncs.com/gj_pet/pet-backend:v0.2.1`
- `FRONTEND_IMAGE=crpi-xxx-vpc.cn-beijing.personal.cr.aliyuncs.com/gj_pet/pet-frontend:v0.2.1`

### 4.2 AIService 环境（`deploy/env/aiservice.env`）

当前 AIService 已经不是单 Prompt 结构，生产配置需要覆盖以下几组变量：

基础运行：

- `APP_ENV`
- `LOG_LEVEL`
- `REDIS_HOST`
- `REDIS_PORT`
- `BACKEND_BASE_URL`
- `BACKEND_TIMEOUT_SECONDS`

模型与外部服务：

- `DASHSCOPE_API_KEY`
- `QWEN_MODEL`
- `AMAP_WEB_SERVICE_KEY`
- `AMAP_BASE_URL`
- `AMAP_SEARCH_PAGE_SIZE`

RAG：

- `RAG_ENABLED`
- `RAG_DATA_DIR`
- `RAG_ACTIVE_FILE`
- `RAG_KNOWLEDGE_DIR`
- `RAG_INDEX_DIR`
- `RAG_TOP_K`
- `RAG_AUTO_LOAD_ON_START`
- `RAG_EMBEDDING_MODEL`
- `RAG_EMBEDDING_MODEL_PATH`

Prompt 与 Tool：

- `BASE_SYSTEM_PROMPT_FILE`
- `QUESTION_REWRITE_PROMPT_FILE`
- `DECISION_PROMPT_FILE`
- `FINAL_RESPONSE_PROMPT_FILE`
- `TOOL_REGISTRY_PROMPT_FILE`
- `WEIGHT_ANALYSIS_TOOL_PROMPT_FILE`
- `TOOL_ENABLED_LIST`
- `WEIGHT_ANALYSIS_LIMIT`

### 4.3 后端环境（`deploy/env/backend.env`）

重点项：

- `JWT_SECRET`
- `AI_PROVIDER`
- `AI_SERVICE_BASE_URL`
- `MAX_FILE_SIZE=20MB`
- `MAX_REQUEST_SIZE=25MB`

### 4.4 前端环境（`deploy/env/frontend.env`）

重点项：

- `BACKEND_UPSTREAM=http://pet-backend:8080`
- `CLIENT_MAX_BODY_SIZE=25m`

## 5. AIService 生产说明

### 5.1 AIService 当前能力

当前 AIService 已接入：

- Question Rewrite 前置模块
- 两阶段对话决策
- `weight_analysis` Tool
- `location_search` Tool
- 本地版本化 RAG

这意味着生产配置不能再只提供单个 `SYSTEM_PROMPT_FILE`，必须与当前分层 Prompt 结构保持一致。

### 5.1.1 当前线上 RAG 状态

当前线上服务器内存较小，开启本地 RAG 后容易触发 OOM，导致 `pet-aiservice` 容器被系统杀掉。

因此当前线上默认策略是：

- `AIService` 正常启用
- 基础对话、Question Rewrite、Tool 路由正常启用
- **RAG 默认关闭**

当前线上建议配置：

- `RAG_ENABLED=false`

补充说明：

- 代码仍保留 RAG 能力
- 当前服务已支持在启动后通过管理接口手动加载 RAG
- 但在现有服务器资源下，不建议线上长期启用 RAG

### 5.2 AIService 日志目录

AIService 日志写入容器内：

- `/app/AIlog`

并挂载到宿主机：

- `${AI_LOG_DIR}`，默认 `/srv/pet/AIlog`

### 5.3 AIService RAG 数据目录

本地 RAG 数据保存在容器内：

- `/app/data`

并挂载到宿主机：

- `${RAG_DATA_DIR}`，默认 `/srv/pet/aiservice/data`

该目录中会包含：

- `knowledge/{version}/rag_chunks.jsonl`
- `indexes/{version}/faiss.index`
- `indexes/{version}/metadata.json`
- `indexes/{version}/manifest.json`
- `active_kb.json`

如果不挂载该目录，容器重建后知识库版本、索引和激活状态都会丢失。

### 5.4 AIService 上游地址

后端通过环境变量配置 AIService 地址：

- `AI_SERVICE_BASE_URL`

容器部署下建议使用同一 docker 网络中的服务名，例如：

- `http://pet-aiservice:8001`

## 6. 上传文件链路

### 6.1 写入链路

- 上传请求 -> 后端 `/api/uploads/...`
- 后端写入容器目录 `/app/uploads`
- `/app/uploads` 挂载到宿主机 `${UPLOADS_DIR}`（默认 `/srv/pet/uploads`）

### 6.2 访问链路

- 浏览器访问 `/uploads/...`
- 前端 Nginx 直接 `alias /data/uploads/`
- `/data/uploads` 来自宿主机 `${UPLOADS_DIR}` 的只读挂载

## 7. 上传大小限制

当前限制策略：

- 前端 Nginx：`client_max_body_size=25m`
- 后端单文件：`MAX_FILE_SIZE=20MB`
- 后端整请求：`MAX_REQUEST_SIZE=25MB`

## 8. 启动顺序

在 `/srv/pet/deploy` 执行：

```bash
docker compose -f docker-compose.mysql.yml up -d
docker compose -f docker-compose.redis.yml up -d
docker compose -f docker-compose.aiservice.yml up -d
docker compose -f docker-compose.backend.yml up -d
docker compose -f docker-compose.frontend.yml up -d
```

## 9. 更新 AIService / 前后端（不重启 MySQL / Redis）

```bash
docker compose -f docker-compose.aiservice.yml pull
docker compose -f docker-compose.backend.yml pull
docker compose -f docker-compose.frontend.yml pull

docker compose -f docker-compose.aiservice.yml up -d
docker compose -f docker-compose.backend.yml up -d
docker compose -f docker-compose.frontend.yml up -d
```

## 10. 离线发布方式（无镜像仓库）

本地构建并导出：

```bash
docker build -t pet-aiservice:v1 ./AIService
docker build -t pet-backend:v1 ./backend
docker build -t pet-frontend:v1 ./frontend

docker save -o pet-aiservice-v1.tar pet-aiservice:v1
docker save -o pet-backend-v1.tar pet-backend:v1
docker save -o pet-frontend-v1.tar pet-frontend:v1
```

或直接使用：

- `deploy/build-export-image.ps1`

说明：

- 该脚本现在支持两类动作：
  - 导出 tar
  - 推送阿里云镜像仓库
- 脚本当前只按输入的版本号构建和推送，不再额外推 `latest`
- 不负责同步宿主机 `RAG_DATA_DIR` 中的知识库和索引数据
- 如果本次版本需要预置知识库，需额外同步 `${RAG_DATA_DIR}` 或在服务器上执行 `/kb/rebuild` 与 `/kb/switch`

上传到服务器后导入：

```bash
docker load -i /srv/pet/pet-aiservice-v1.tar
docker load -i /srv/pet/pet-backend-v1.tar
docker load -i /srv/pet/pet-frontend-v1.tar
```

然后更新 `deploy/.env`：

- `AISERVICE_IMAGE=pet-aiservice:v1`
- `BACKEND_IMAGE=pet-backend:v1`
- `FRONTEND_IMAGE=pet-frontend:v1`

## 10.1 通过阿里云镜像仓库发布

如果使用阿里云个人版镜像仓库，推荐直接在 `deploy/.env` 中维护完整镜像地址。

示例：

```env
AISERVICE_IMAGE=crpi-hj866eohic2eueoi-vpc.cn-beijing.personal.cr.aliyuncs.com/gj_pet/pet-aiservice:v0.2.1
BACKEND_IMAGE=crpi-hj866eohic2eueoi-vpc.cn-beijing.personal.cr.aliyuncs.com/gj_pet/pet-backend:v0.2.1
FRONTEND_IMAGE=crpi-hj866eohic2eueoi-vpc.cn-beijing.personal.cr.aliyuncs.com/gj_pet/pet-frontend:v0.2.1
```

然后在服务器执行：

```bash
docker compose -f docker-compose.aiservice.yml pull
docker compose -f docker-compose.backend.yml pull
docker compose -f docker-compose.frontend.yml pull

docker compose -f docker-compose.aiservice.yml up -d
docker compose -f docker-compose.backend.yml up -d
docker compose -f docker-compose.frontend.yml up -d
```

## 11. RAG 知识库发布流程

说明：当前线上默认不启用 RAG。以下流程更适合本地环境、测试环境，或已升级内存资源后的服务器环境。

如果本次发版涉及知识库更新，建议按以下流程执行：

1. 将新的 `rag_chunks.jsonl` 放入：
   - `${RAG_DATA_DIR}/knowledge/{version}/rag_chunks.jsonl`
2. 调用：
   - `POST /kb/rebuild`
3. 构建成功后调用：
   - `POST /kb/switch`
4. 校验：
   - `GET /kb/current`
5. 如果服务最初以 `RAG_ENABLED=false` 启动，额外调用：
   - `POST /kb/enable`

示例：

```bash
curl -X POST http://127.0.0.1:8001/kb/rebuild \
  -H 'Content-Type: application/json' \
  -d '{"version":"v0402"}'

curl -X POST http://127.0.0.1:8001/kb/switch \
  -H 'Content-Type: application/json' \
  -d '{"version":"v0402"}'

curl -X POST http://127.0.0.1:8001/kb/enable \
  -H 'Content-Type: application/json' \
  -d '{}'

curl http://127.0.0.1:8001/kb/current
```

## 12. 验收检查清单

### 12.1 容器状态

```bash
docker ps
```

应看到：

- `pet-mysql`
- `pet-redis`
- `pet-aiservice`
- `pet-backend`
- `pet-frontend`

### 12.2 后端健康检查

```bash
curl http://127.0.0.1:8080/actuator/health
```

### 12.3 AIService 健康检查

```bash
curl http://127.0.0.1:8001/health
```

### 12.4 前端可访问

```bash
curl -I http://127.0.0.1/
```

### 12.5 上传目录直读验证

```bash
echo hello-upload > /srv/pet/uploads/test.txt
curl -i http://127.0.0.1/uploads/test.txt
```

### 12.6 AIService 连通性验证

确认后端配置的 `AI_SERVICE_BASE_URL` 可访问，并检查后端 AI 对话接口是否正常返回。

### 12.7 AIService 日志验证

```bash
ls -l /srv/pet/AIlog
find /srv/pet/AIlog -maxdepth 2 -type f | tail -n 20
```

### 12.8 RAG 当前版本验证

```bash
curl http://127.0.0.1:8001/kb/current
ls -l /srv/pet/aiservice/data
```

如果线上当前就是按低内存模式运行，`loaded_version` 为空是预期结果。

## 13. 常见问题排查

### 13.1 后端启动报 `no main manifest attribute`

原因：jar 不是 Spring Boot 可执行包。  
处理：确认 `spring-boot-maven-plugin` 已配置 `repackage`，重新构建镜像。

### 13.2 AIService 启动了但地点搜索不可用

检查：

- `AMAP_WEB_SERVICE_KEY` 是否已配置
- 服务器是否允许访问高德 Web Service

### 13.3 AIService 启动了但 RAG 不生效

检查：

- 当前服务器是否本来就按低内存策略设置了 `RAG_ENABLED=false`
- 如果当前环境确实需要 RAG：
  - `RAG_ENABLED=true`，或服务启动后手动调用 `POST /kb/enable`
- `/app/data` 是否已正确挂载
- `GET /kb/current` 是否存在已激活版本
- `AIlog` 中是否有检索或加载失败日志

### 13.3.1 AIService 容器反复重启并出现 OOM

如果 `docker events` 或 `dmesg` 中出现：

- `exitCode=137`
- `Out of memory`
- `Killed process ... python`

通常说明服务器内存不足。

当前处理建议：

- 先设置 `RAG_ENABLED=false`
- 保持 AI 基础对话链路运行
- 暂不在该服务器默认开启 RAG
- 如需长期启用 RAG，优先升级服务器内存或拆分独立高内存服务

### 13.4 上传报 `too large` / `413`

检查：

- 前端 `CLIENT_MAX_BODY_SIZE`
- 后端 `MAX_FILE_SIZE` / `MAX_REQUEST_SIZE`

### 13.5 上传 500 但无明显日志

优先检查挂载目录权限：

```bash
docker exec -it pet-backend sh -c 'echo test > /app/uploads/_write_test.txt'
```

若失败，调整宿主机目录权限。
