"""聊天 API 路由。

API 层只处理 HTTP 协议相关的工作：

- 接收请求
- 获取依赖
- 调用编排器
- 返回响应
"""

from fastapi import APIRouter, Depends, Request

from ai_service.orchestrators.chat_orchestrator import ChatOrchestrator
from ai_service.schemas.chat_request import ChatRequest
from ai_service.schemas.chat_response import ChatResponse

router = APIRouter(prefix="/api/ai", tags=["ai"])


def get_orchestrator(request: Request) -> ChatOrchestrator:
    """从应用上下文中获取全局聊天编排器。"""
    return request.app.state.chat_orchestrator


@router.post("/chat", response_model=ChatResponse)
async def chat(
    payload: ChatRequest,
    orchestrator: ChatOrchestrator = Depends(get_orchestrator),
) -> ChatResponse:
    """AI 聊天主入口。

    Java 后端只需要调用这个入口，不需要关心服务内部的编排细节。
    """
    return await orchestrator.handle(payload)
