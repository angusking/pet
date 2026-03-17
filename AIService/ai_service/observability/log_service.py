"""AI 调用日志服务。"""

import json
from datetime import datetime
from pathlib import Path

from ai_service.schemas.chat_request import ChatRequest
from ai_service.schemas.chat_response import ChatResponse


class LogService:
    """将单次 AI 对话调用写入独立日志文件。"""

    def __init__(self, log_dir: str) -> None:
        self._base_dir = Path(log_dir)

    def log_success(
        self,
        request: ChatRequest,
        response: ChatResponse,
        model: str,
        latency_ms: int,
        usage: dict,
        used_rewrite: bool,
        used_rag: bool,
        used_tool: bool,
    ) -> None:
        """记录成功调用。"""
        now = datetime.now()
        content = self._build_content(
            now=now,
            request=request,
            status="success",
            model=model,
            latency_ms=latency_ms,
            used_rewrite=used_rewrite,
            used_rag=used_rag,
            used_tool=used_tool,
            response=response,
            usage=usage,
            error=None,
        )
        self._write_file(now, request.requestId, "send", content)

    def log_error(
        self,
        request: ChatRequest,
        model: str,
        latency_ms: int,
        error: str,
        used_rewrite: bool,
        used_rag: bool,
        used_tool: bool,
    ) -> None:
        """记录失败调用。"""
        now = datetime.now()
        content = self._build_content(
            now=now,
            request=request,
            status="error",
            model=model,
            latency_ms=latency_ms,
            used_rewrite=used_rewrite,
            used_rag=used_rag,
            used_tool=used_tool,
            response=None,
            usage={},
            error=error,
        )
        self._write_file(now, request.requestId, "send_error", content)

    def _build_content(
        self,
        *,
        now: datetime,
        request: ChatRequest,
        status: str,
        model: str,
        latency_ms: int,
        used_rewrite: bool,
        used_rag: bool,
        used_tool: bool,
        response: ChatResponse | None,
        usage: dict,
        error: str | None,
    ) -> str:
        sections: list[str] = [
            "=== AI CALL DEBUG LOG ===",
            f"time={now.isoformat()}",
            f"traceId={request.requestId}",
            f"status={status}",
            "provider=aiservice",
            f"model={model or 'unknown'}",
            f"userId={request.userId}",
            f"sessionId={request.conversationId}",
            f"petId={request.pet.petId if request.pet else 'null'}",
            "",
            "[REQUEST]",
            f"message.length={len(request.message)}",
            f"message.preview={self._preview(request.message)}",
            f"recentMessages.count={len(request.recentMessages)}",
            f"recentMessages.preview={self._preview(self._dump_recent_messages(request))}",
            "",
            "[PET_CONTEXT]",
        ]

        if request.pet is None:
            sections.append("none")
        else:
            sections.extend(
                [
                    f"id={request.pet.petId}",
                    f"name={request.pet.name}",
                    f"type={request.pet.type}",
                    f"age={request.pet.age}",
                    f"weightKg={request.pet.weight}",
                ]
            )

        sections.extend(
            [
                "",
                "[ORCHESTRATION]",
                f"latencyMs={latency_ms}",
                f"usedRewrite={used_rewrite}",
                f"usedRag={used_rag}",
                f"usedTool={used_tool}",
                f"usage={json.dumps(usage, ensure_ascii=False)}",
                "",
                "[RESPONSE]",
            ]
        )

        if response is None:
            sections.append("none")
        else:
            sections.extend(
                [
                    f"riskLevel={response.riskLevel.value}",
                    f"answer.preview={self._preview(response.answer)}",
                    f"response.full={self._as_json(response.model_dump())}",
                ]
            )

        sections.extend(
            [
                "",
                "[ERROR]",
                "none" if not error else error,
                "",
            ]
        )
        return "\n".join(sections)

    def _write_file(self, now: datetime, trace_id: str, suffix: str, content: str) -> None:
        date_dir = self._base_dir / now.strftime("%Y-%m-%d")
        date_dir.mkdir(parents=True, exist_ok=True)
        file_name = f"{now.strftime('%Y%m%d-%H%M%S-%f')[:-3]}_{self._sanitize(trace_id)}_{suffix}.txt"
        (date_dir / file_name).write_text(content, encoding="utf-8")

    def _sanitize(self, value: str) -> str:
        return "".join(ch if ch.isalnum() or ch in {"-", "_"} else "_" for ch in value)

    def _preview(self, value: str | None, max_len: int = 500) -> str:
        if not value:
            return "null"
        normalized = value.replace("\r", "\\r").replace("\n", "\\n")
        return normalized if len(normalized) <= max_len else normalized[:max_len] + "..."

    def _dump_recent_messages(self, request: ChatRequest) -> str:
        return self._as_json([message.model_dump() for message in request.recentMessages])

    def _as_json(self, payload: object) -> str:
        return json.dumps(payload, ensure_ascii=False)
