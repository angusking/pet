# AIService 短期记忆与历史回源

当前 AIService 的多轮对话上下文恢复顺序如下：

1. 优先读取 Redis 中的短期记忆。
2. 如果 Redis miss，则使用 backend 在 `/api/ai/chat` 请求里传来的 `recentMessages`。
3. 如果请求里也没有最近消息，则调用 backend 内部接口 `/internal/ai/chats/{sessionId}/recent-messages` 回源最近消息。
4. 一旦通过第 2 或第 3 步恢复成功，会重新写回 Redis，供下一轮直接命中。

这套分工的目的：

- Java backend 持久化完整会话和消息历史。
- AIService 只管理短期记忆和上下文恢复策略。
- Redis 丢失或过期后，AIService 仍然可以通过 backend 恢复最近几轮对话。

## 新增配置

AIService 新增了两项配置：

- `BACKEND_BASE_URL`
  - backend 服务地址
  - 本地默认值：`http://localhost:8080`
  - 部署环境示例：`http://pet-backend:8080`

- `BACKEND_TIMEOUT_SECONDS`
  - AIService 回源 backend 内部接口时的超时时间
  - 默认值：`5`

## backend 内部接口

backend 新增内部接口：

- `GET /internal/ai/chats/{sessionId}/recent-messages?userId={userId}&limit={limit}`

返回最近若干条轻量消息，仅用于 AIService 在 Redis miss 时恢复上下文，不面向前端使用。
