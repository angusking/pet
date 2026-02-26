# 生产部署说明（2C2G / 40G）

适用环境：

- 服务器：`2C2G / 40G`
- 部署方式：`前端 / 后端 / MySQL / Redis` 四个独立容器
- 编排方式：`docker compose`（按服务拆分为 4 个文件）

## 1. 方案结论（按你的要求）

### 为什么拆成 4 个 compose 文件

目标是“每个服务单独处理、不互相影响”，尤其是：

- 更新前后端时，不重启 `MySQL` / `Redis`
- 数据库和缓存长期稳定运行
- 运维动作更细粒度，降低误操作风险

已拆分为：

- `deploy/docker-compose.mysql.yml`
- `deploy/docker-compose.redis.yml`
- `deploy/docker-compose.backend.yml`
- `deploy/docker-compose.frontend.yml`

## 2. 共享网络（关键）

因为四个 compose 文件是分开启动的，容器之间不能自动共享网络。

当前方案使用一个共享外部网络：

- 网络名：`pet-network`

四个 compose 文件都配置为加入该网络，因此容器可以通过容器名互相访问：

- 后端访问 MySQL：`pet-mysql:3306`
- 后端访问 Redis：`pet-redis:6379`
- 前端反向代理后端：`http://pet-backend:8080`

### 首次部署前先创建网络（必须）

```bash
docker network create pet-network
```

只需执行一次。

## 3. 目录规划（宿主机）

建议目录：

```bash
/srv/pet/
├── mysql/data
├── redis/data
├── uploads
├── AIlog
└── deploy
```

说明：

- `uploads`：用户上传文件（头像/帖子图片等）
- `AIlog`：AI 调试日志（文本文件）
- `mysql/data`、`redis/data`：持久化数据
- `deploy`：compose 文件 + 外挂配置

## 4. 你的要求 1：MySQL / Redis 数据映射到宿主机

已在 compose 中按宿主机目录映射（默认值）：

- MySQL：`/srv/pet/mysql/data` -> `/var/lib/mysql`
- Redis：`/srv/pet/redis/data` -> `/data`

你也可以通过环境变量覆盖：

- `MYSQL_DATA_DIR`
- `REDIS_DATA_DIR`

## 5. 你的要求 2：上传路径映射 + 前端代理访问图片

### 后端上传目录建议（宿主机）

建议使用：

- `/srv/pet/uploads`

原因：

- 与代码、镜像完全解耦
- 更新镜像不影响上传文件
- 便于备份与迁移

### 后端容器内上传目录

后端 compose 固定映射到：

- 容器内：`/app/uploads`

并通过环境变量指定：

- `UPLOAD_BASE_DIR=/app/uploads`

### 前端容器直接读取图片目录（推荐）

前端 Nginx 已配置为直接读取挂载目录：

- 宿主机 `${UPLOADS_DIR}` -> 前端容器 `/data/uploads`（只读）
- Nginx `location /uploads/` 使用 `alias /data/uploads/`

因此前端页面直接用 `/uploads/...` 即可：

- 图片请求不经过后端，降低后端压力
- 前端容器可直接作为图片静态文件服务
- 后端仅负责上传写入宿主机目录

## 6. 你的要求 3：是否单独列生产外挂配置文件

结论：**建议，且已按此方案落地。**

这样可以做到：

- 以后只更新镜像，不改容器内配置
- 配置与镜像解耦，便于回滚
- 测试/预发/生产可复用同一镜像，只替换配置

### 当前 deploy 目录结构

```bash
deploy/
├── docker-compose.mysql.yml
├── docker-compose.redis.yml
├── docker-compose.backend.yml
├── docker-compose.frontend.yml
├── .env
├── .env.example
├── env/
│   ├── mysql.env
│   ├── backend.env
│   └── frontend.env
└── config/
    └── backend/
        ├── application.yml
        └── application-ai.yml
```

### 说明

- `deploy/.env`
  - compose 级公共变量（镜像地址/tag、宿主机目录、后端上游地址等）
- `deploy/.env.example`
  - 脱敏模板；新环境部署时先复制为 `deploy/.env` 再修改
- `env/*.env`
  - 容器环境变量（密码、JWT、API Key、镜像参数等）
- `config/backend/*.yml`
  - Spring Boot 外挂配置（业务配置）

## 7. 启动顺序（首次部署）

### 7.1 创建宿主机目录

```bash
sudo mkdir -p /srv/pet/mysql/data /srv/pet/redis/data /srv/pet/uploads /srv/pet/AIlog
sudo mkdir -p /srv/pet/deploy/env /srv/pet/deploy/config/backend
```

### 7.2 创建共享网络（必须）

```bash
docker network create pet-network
```

### 7.3 上传 deploy 目录内容到服务器

建议放到：

- `/srv/pet/deploy`

### 7.4 修改生产配置（必须）

重点修改：

- `deploy/.env`
  - `BACKEND_IMAGE` / `FRONTEND_IMAGE`（改成你的镜像仓库地址与版本 tag）
  - 宿主机目录路径（如不是 `/srv/pet/...`）
- `deploy/env/mysql.env`
  - `MYSQL_ROOT_PASSWORD`
  - `MYSQL_PASSWORD`
- `deploy/env/backend.env`
  - `JWT_SECRET`
  - `DASHSCOPE_API_KEY`
  - `AI_PROVIDER`（生产建议 `qwen`）
- `deploy/env/frontend.env`
  - 通常默认即可（除非你要改后端代理上游）

### 7.5 按顺序启动

在服务器 `deploy` 目录执行：

```bash
docker compose -f docker-compose.mysql.yml up -d
docker compose -f docker-compose.redis.yml up -d
docker compose -f docker-compose.backend.yml up -d
docker compose -f docker-compose.frontend.yml up -d
```

## 8. 更新系统（前后端）且不重启 MySQL / Redis

这正是拆分后的主要收益。

### 仅更新后端

```bash
docker compose -f docker-compose.backend.yml pull
docker compose -f docker-compose.backend.yml up -d
```

### 仅更新前端

```bash
docker compose -f docker-compose.frontend.yml pull
docker compose -f docker-compose.frontend.yml up -d
```

### 同时更新前后端（不动 MySQL / Redis）

```bash
docker compose -f docker-compose.backend.yml pull
docker compose -f docker-compose.frontend.yml pull
docker compose -f docker-compose.backend.yml up -d
docker compose -f docker-compose.frontend.yml up -d
```

说明：

- `mysql`、`redis` 容器不会受影响
- 上传文件、数据库、Redis 数据均保留在宿主机
- 镜像版本建议通过 `deploy/.env` 中的 `BACKEND_IMAGE` / `FRONTEND_IMAGE` 统一管理

## 9. 日常维护命令

### 查看某个服务日志

```bash
docker compose -f docker-compose.backend.yml logs -f
docker compose -f docker-compose.frontend.yml logs -f
docker compose -f docker-compose.mysql.yml logs -f
docker compose -f docker-compose.redis.yml logs -f
```

### 重启单个服务

```bash
docker compose -f docker-compose.backend.yml restart
docker compose -f docker-compose.frontend.yml restart
```

### 停止单个服务（不影响其他服务）

```bash
docker compose -f docker-compose.backend.yml stop
docker compose -f docker-compose.frontend.yml stop
```

## 10. 2C2G 环境建议（补充）

- 建议开启 `swap`（如 2G），降低高峰 OOM 风险
- 后端 JVM 维持当前配置：`-Xms256m -Xmx512m`
- MySQL/Redis 如非必须，不建议对公网开放端口（生产可移除 `ports`）
- AI 调试日志增长较快，建议定期清理 `AIlog`
- 生产环境建议使用固定镜像 tag，不要长期使用 `latest`
