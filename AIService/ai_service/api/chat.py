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
from ai_service.providers.memory.base import MemoryProvider
from ai_service.schemas.chat_request import ChatRequest
from ai_service.schemas.chat_response import ChatResponse

router = APIRouter(prefix="/api/ai", tags=["ai"])
internal_router = APIRouter(prefix="/internal/ai", tags=["ai-internal"])


def get_orchestrator(request: Request) -> ChatOrchestrator:
    """从应用上下文中获取全局聊天编排器。"""
    return request.app.state.chat_orchestrator


def get_memory_provider(request: Request) -> MemoryProvider:
    """从应用上下文中获取全局记忆 Provider。"""
    return request.app.state.memory_provider


@router.post("/chat", response_model=ChatResponse)
async def chat(
    payload: ChatRequest,
    orchestrator: ChatOrchestrator = Depends(get_orchestrator),
) -> ChatResponse:
    """AI 聊天主入口。"""
    return await orchestrator.handle(payload)


@internal_router.delete("/memory/{conversation_id}")
async def clear_memory(
    conversation_id: str,
    memory_provider: MemoryProvider = Depends(get_memory_provider),
) -> dict[str, str]:
    """清理指定会话的 Redis 短期记忆。

    这个接口只给 backend 内部调用，用于用户删除某个 AI 会话时，
    同步把 AIService 的短期记忆窗口一起删除。
    """
    await memory_provider.delete_messages(conversation_id)
    return {"status": "ok", "conversationId": conversation_id}
