"""聊天 API 路由。

API 层只处理 HTTP 相关工作：
- 接收请求
- 校验输入
- 从应用上下文拿依赖
- 调用编排器
- 返回响应

它不应该承担业务编排，否则路由代码会越来越重。
"""

from fastapi import APIRouter, Depends, Request

from ai_service.orchestrators.chat_orchestrator import ChatOrchestrator
from ai_service.schemas.chat_request import ChatRequest
from ai_service.schemas.chat_response import ChatResponse

router = APIRouter(prefix="/api/ai", tags=["ai"])


def get_orchestrator(request: Request) -> ChatOrchestrator:
    """从应用上下文中获取全局聊天编排器。

    之所以不用每次在路由里现场 new 一个编排器，是因为：
    - 编排器依赖 Redis provider 等共享资源
    - 这些依赖应该在应用启动时统一初始化
    """
    return request.app.state.chat_orchestrator


@router.post("/chat", response_model=ChatResponse)
async def chat(
    payload: ChatRequest,
    orchestrator: ChatOrchestrator = Depends(get_orchestrator),
) -> ChatResponse:
    """AI 聊天主入口。

    Java backend 只需要调这个接口，不需要关心：
    - Redis 短期记忆怎么取
    - RAG / tool 怎么串
    - 输出怎么兜底
    这些都交给编排器处理。
    """
    return await orchestrator.handle(payload)
