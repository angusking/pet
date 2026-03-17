package com.pet.api.ai.dto;

import java.time.LocalDateTime;

/**
 * 提供给 AIService 回源最近上下文时使用的轻量消息结构。
 *
 * <p>这里故意不返回 model、tokens 等展示字段，
 * 因为 AIService 在 Redis miss 时只需要恢复最近几轮对话内容本身。
 */
public record AiRecentChatMessageResponse(
    String role,
    String content,
    LocalDateTime createdAt
) {}
