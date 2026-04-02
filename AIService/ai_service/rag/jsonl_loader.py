"""JSONL 知识文件加载器。"""

from __future__ import annotations

import json
import re
from pathlib import Path

from ai_service.rag.exceptions import IndexBuildError
from ai_service.rag.schemas import RagChunk


class JsonlKnowledgeLoader:
    """把版本目录中的 rag_chunks.jsonl 读取成统一的分块对象列表。

    当前 loader 不再假定输入一定是极简 `id/text` 结构，
    而是显式兼容当前知识文件的实际字段：
    - `chunk_id` -> `id`
    - `content` -> `text`
    其余字段则收进 metadata，并尽量合成更有用的 title/category。
    """

    _META_EXCLUDE_KEYS = {
        "doc_id",
        "source",
        "chunk_id",
        "chunk_type",
        "content",
        "tags",
        "part_title",
        "chapter_title",
        "section_title",
        "subtopic_title",
        "quality_score",
        "page_start",
        "page_end",
    }

    def load(self, file_path: Path) -> list[RagChunk]:
        """读取并校验知识文件。"""

        if not file_path.exists():
            raise IndexBuildError(f"知识文件不存在: {file_path}")

        chunks: list[RagChunk] = []
        with file_path.open("r", encoding="utf-8") as handle:
            for line_number, raw_line in enumerate(handle, start=1):
                line = raw_line.strip()
                if not line:
                    continue
                try:
                    payload = json.loads(line)
                except json.JSONDecodeError as exc:
                    raise IndexBuildError(f"知识文件第 {line_number} 行不是合法 JSON。") from exc

                try:
                    chunk = self._normalize_payload(payload)
                except Exception as exc:
                    raise IndexBuildError(
                        f"知识文件第 {line_number} 行字段不完整，至少需要 chunk_id/content 或 id/text。"
                    ) from exc
                chunks.append(chunk)

        if not chunks:
            raise IndexBuildError(f"知识文件为空，无法构建索引: {file_path}")
        return chunks

    def _normalize_payload(self, payload: dict) -> RagChunk:
        chunk_id = str(payload.get("id") or payload.get("chunk_id") or "").strip()
        text = str(payload.get("text") or payload.get("content") or "").strip()
        if not chunk_id or not text:
            raise ValueError("missing required fields")

        source = str(payload.get("source") or "").strip()
        tags = self._normalize_tags(payload.get("tags"))
        title = self._build_title(payload)
        category = self._build_category(payload)
        metadata = self._build_metadata(payload)

        return RagChunk(
            id=chunk_id,
            text=text,
            title=title,
            source=source,
            category=category,
            tags=tags,
            metadata=metadata,
        )

    def _build_title(self, payload: dict) -> str:
        """把多层章节信息压成一个更适合展示和 Prompt 引用的标题。"""

        candidates = [
            payload.get("part_title"),
            payload.get("chapter_title"),
            payload.get("section_title"),
            payload.get("subtopic_title"),
        ]
        cleaned_parts = [
            self._clean_heading(str(item))
            for item in candidates
            if isinstance(item, str) and self._clean_heading(item)
        ]
        if cleaned_parts:
            return " / ".join(dict.fromkeys(cleaned_parts))
        return self._clean_heading(str(payload.get("source") or "")) or str(payload.get("chunk_id") or "")

    def _build_category(self, payload: dict) -> str:
        """优先用篇章或章节信息作为检索分类。"""

        for key in ("part_title", "chapter_title", "section_title"):
            value = payload.get(key)
            if isinstance(value, str):
                cleaned = self._clean_heading(value)
                if cleaned:
                    return cleaned
        return str(payload.get("chunk_type") or "").strip()

    def _build_metadata(self, payload: dict) -> dict:
        metadata = {
            "doc_id": str(payload.get("doc_id") or "").strip(),
            "chunk_type": str(payload.get("chunk_type") or "").strip(),
            "page_start": payload.get("page_start"),
            "page_end": payload.get("page_end"),
            "part_title": self._clean_heading(str(payload.get("part_title") or "")),
            "chapter_title": self._clean_heading(str(payload.get("chapter_title") or "")),
            "section_title": self._clean_heading(str(payload.get("section_title") or "")),
            "subtopic_title": self._clean_heading(str(payload.get("subtopic_title") or "")),
            "quality_score": payload.get("quality_score"),
            "char_count": payload.get("char_count"),
        }

        # 把未来可能扩展的新字段也保留下来，避免再次改 schema。
        for key, value in payload.items():
            if key in self._META_EXCLUDE_KEYS:
                continue
            metadata[key] = value
        return metadata

    def _normalize_tags(self, value: object) -> list[str]:
        if not isinstance(value, list):
            return []
        return [str(item).strip() for item in value if str(item).strip()]

    def _clean_heading(self, value: str) -> str:
        """清理目录页和 OCR 残留里的干扰字符。

        当前数据里会出现：
        - 大量连续点线
        - 目录页括号噪声
        - 多余空白
        这里不做激进清洗，只做对检索展示最有帮助的轻量处理。
        """

        text = value.strip()
        if not text:
            return ""
        text = re.sub(r"[⋯·—\-_=]{3,}", " ", text)
        text = re.sub(r"[（(][^）)]*[）)]", " ", text)
        text = re.sub(r"\s+", " ", text)
        return text.strip(" /")

