# AIService

AIService 是一个独立的 Python AI 编排服务，对 Java 后端暴露统一的 HTTP 接口。

当前版本实现了文档中定义的 V1 能力：

- FastAPI 服务入口
- `POST /api/ai/chat` 主接口
- Qwen 大模型调用封装
- Redis 短期记忆
- Prompt 统一管理
- 结构化响应解析与校验
- 基础安全规则
- AI 调用日志

## 本地启动

```powershell
cd D:\pet\AIService
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
python run.py
```

## 主要接口

- `GET /health`
- `POST /api/ai/chat`

## 环境变量

参考 `.env.example`
