# 生产部署说明（2C2G / 40G）

本文基于当前项目的最新部署结构，适用于：

- 前端、后端、MySQL、Redis 独立容器
- 四个独立 compose 文件
- 前端 Nginx 直接读取宿主机上传目录
- 后端 AI 提示词文件外挂挂载

## 1. 当前部署文件结构

`deploy/` 目录：

- `docker-compose.mysql.yml`
- `docker-compose.redis.yml`
- `docker-compose.backend.yml`
- `docker-compose.frontend.yml`
- `.env`
- `.env.example`
- `env/mysql.env`
- `env/backend.env`
- `env/frontend.env`
- `config/backend/application.yml`
- `config/backend/application-ai.yml`
- `config/backend/prompts/ai-system-prompt-v2.md`
- `config/frontend/nginx.conf`

## 2. 服务拆分与更新原则

服务拆分：

- MySQL：`docker-compose.mysql.yml`
- Redis：`docker-compose.redis.yml`
- Backend：`docker-compose.backend.yml`
- Frontend：`docker-compose.frontend.yml`

更新原则：

- 更新前后端时，不重启 MySQL / Redis
- 只执行前后端 compose 文件

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

- `BACKEND_IMAGE`
- `FRONTEND_IMAGE`
- `MYSQL_DATA_DIR`
- `REDIS_DATA_DIR`
- `UPLOADS_DIR`
- `AI_LOG_DIR`
- `BACKEND_UPSTREAM`

建议每次发版只改镜像 tag。

### 4.2 后端环境（`deploy/env/backend.env`）

重点项：

- `JWT_SECRET`
- `DASHSCOPE_API_KEY`
- `AI_PROVIDER`
- `QWEN_SYSTEM_PROMPT_FILE=/app/config/prompts/ai-system-prompt-v2.md`
- `MAX_FILE_SIZE=20MB`
- `MAX_REQUEST_SIZE=25MB`

### 4.3 前端环境（`deploy/env/frontend.env`）

重点项：

- `BACKEND_UPSTREAM=http://pet-backend:8080`
- `CLIENT_MAX_BODY_SIZE=25m`

### 4.4 AI 提示词外挂文件

当前已通过后端 compose 挂载：

- 宿主机（deploy 相对路径）：`./config/backend/prompts`
- 容器内路径：`/app/config/prompts`

后端从以下路径读取提示词：

- `/app/config/prompts/ai-system-prompt-v2.md`

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
docker compose -f docker-compose.backend.yml up -d
docker compose -f docker-compose.frontend.yml up -d
```

## 8. 更新前后端（不重启 MySQL / Redis）

```bash
docker compose -f docker-compose.backend.yml pull
docker compose -f docker-compose.frontend.yml pull
docker compose -f docker-compose.backend.yml up -d
docker compose -f docker-compose.frontend.yml up -d
```

## 9. 你当前常用的离线发布方式（无镜像仓库）

本地构建并导出：

```bash
docker build -t pet-backend:v1 ./backend
docker build -t pet-frontend:v1 ./frontend
docker save -o pet-backend-v1.tar pet-backend:v1
docker save -o pet-frontend-v1.tar pet-frontend:v1
```

上传到服务器后导入：

```bash
docker load -i /srv/pet/pet-backend-v1.tar
docker load -i /srv/pet/pet-frontend-v1.tar
```

然后更新 `deploy/.env`：

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

### 10.5 AI 提示词挂载验证

```bash
docker exec -it pet-backend ls -l /app/config/prompts/ai-system-prompt-v2.md
docker exec -it pet-backend sh -c 'head -n 5 /app/config/prompts/ai-system-prompt-v2.md'
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
