# 生产部署说明（2C2G / 40G）

本文基于当前项目的最新部署结构，适用于：

- 前端、后端、AIService、MySQL、Redis 独立容器
- 五个独立 compose 文件
- 前端 Nginx 直接读取宿主机上传目录
- 后端通过 HTTP 调用独立 AIService
- AIService 日志写入宿主机 `AIlog` 目录

## 1. 当前部署文件结构

`deploy/` 目录：

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
- `config/backend/application-ai.yml`
- `config/frontend/nginx.conf`

## 2. 服务拆分与更新原则

服务拆分：

- MySQL：`docker-compose.mysql.yml`
- Redis：`docker-compose.redis.yml`
- AIService：`docker-compose.aiservice.yml`
- Backend：`docker-compose.backend.yml`
- Frontend：`docker-compose.frontend.yml`

更新原则：

- 更新 AIService、前后端时，不重启 MySQL / Redis
- 只执行对应服务的 compose 文件

## 3. 首次部署准备

### 3.1 创建宿主机目录

```bash
sudo mkdir -p /srv/pet/mysql/data
sudo mkdir -p /srv/pet/redis/data
sudo mkdir -p /srv/pet/uploads
sudo mkdir -p /srv/pet/AIlog
sudo mkdir -p /srv/pet/deploy
```

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
- `BACKEND_UPSTREAM`

建议每次发版只改镜像 tag。

### 4.2 AIService 环境（`deploy/env/aiservice.env`）

重点项：

- `DASHSCOPE_API_KEY`
- `AI_LOG_DIR`
- `SYSTEM_PROMPT_FILE`

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

### 4.5 AIService 上游地址

后端通过环境变量配置 AIService 地址：

- `AI_SERVICE_BASE_URL`

容器部署下建议使用同一 docker 网络中的服务名，例如：

- `http://pet-aiservice:8001`

### 4.6 AIService 日志目录

AIService 日志写入容器内：

- `/app/AIlog`

并挂载到宿主机：

- `${AI_LOG_DIR}`，默认 `/srv/pet/AIlog`

## 5. 上传文件链路（当前方案）

### 5.1 写入链路

- 上传请求 -> 后端 `/api/uploads/...`
- 后端写入容器目录 `/app/uploads`
- `/app/uploads` 挂载到宿主机 `${UPLOADS_DIR}`（默认 `/srv/pet/uploads`）

### 5.2 访问链路

- 浏览器访问 `/uploads/...`
- 前端 Nginx 直接 `alias /data/uploads/`
- `/data/uploads` 来自宿主机 `${UPLOADS_DIR}` 的只读挂载

说明：

- 图片访问不再经过后端，降低后端压力

## 6. 上传大小限制（统一策略）

当前限制策略：

- 前端 Nginx：`client_max_body_size=25m`
- 后端单文件：`MAX_FILE_SIZE=20MB`
- 后端整请求：`MAX_REQUEST_SIZE=25MB`

调整建议：

- 先调前端和后端请求上限一致
- 后端单文件略小于请求总量，避免多文件请求意外超限

## 7. 启动顺序

在 `/srv/pet/deploy` 执行：

```bash
docker compose -f docker-compose.mysql.yml up -d
docker compose -f docker-compose.redis.yml up -d
docker compose -f docker-compose.aiservice.yml up -d
docker compose -f docker-compose.backend.yml up -d
docker compose -f docker-compose.frontend.yml up -d
```

## 8. 更新 AIService / 前后端（不重启 MySQL / Redis）

```bash
docker compose -f docker-compose.aiservice.yml pull
docker compose -f docker-compose.backend.yml pull
docker compose -f docker-compose.frontend.yml pull
docker compose -f docker-compose.aiservice.yml up -d
docker compose -f docker-compose.backend.yml up -d
docker compose -f docker-compose.frontend.yml up -d
```

## 9. 常用离线发布方式（无镜像仓库）

本地构建并导出：

```bash
docker build -t pet-aiservice:v1 ./AIService
docker build -t pet-backend:v1 ./backend
docker build -t pet-frontend:v1 ./frontend
docker save -o pet-aiservice-v1.tar pet-aiservice:v1
docker save -o pet-backend-v1.tar pet-backend:v1
docker save -o pet-frontend-v1.tar pet-frontend:v1
```

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

再执行第 8 节更新命令。

## 10. 验收检查清单

### 10.1 容器状态

```bash
docker ps
```

应看到：

- `pet-mysql`
- `pet-redis`
- `pet-aiservice`
- `pet-backend`
- `pet-frontend`

### 10.2 后端健康检查

```bash
curl http://127.0.0.1:8080/actuator/health
```

### 10.3 前端可访问

```bash
curl -I http://127.0.0.1/
```

### 10.4 上传目录直读验证

```bash
echo hello-upload > /srv/pet/uploads/test.txt
curl -i http://127.0.0.1/uploads/test.txt
```

### 10.5 AIService 连通性验证

确认后端配置的 `AI_SERVICE_BASE_URL` 可访问，并检查后端 AI 对话接口是否正常返回。

### 10.6 AIService 日志验证

```bash
ls -l /srv/pet/AIlog
find /srv/pet/AIlog -maxdepth 2 -type f | tail -n 20
```

## 11. 常见问题排查

### 11.1 后端启动报 `no main manifest attribute`

原因：jar 不是 Spring Boot 可执行包。  
处理：确认 `spring-boot-maven-plugin` 已配置 `repackage`，重新构建镜像。

### 11.2 上传报 `too large` / `413`

检查：

- 前端 `CLIENT_MAX_BODY_SIZE`
- 后端 `MAX_FILE_SIZE` / `MAX_REQUEST_SIZE`

### 11.3 上传 500 但无明显日志

优先检查挂载目录权限：

```bash
docker exec -it pet-backend sh -c 'echo test > /app/uploads/_write_test.txt'
```

若失败，调整宿主机目录权限。
