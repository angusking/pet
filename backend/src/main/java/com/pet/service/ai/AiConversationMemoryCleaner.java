package com.pet.service.ai;

/**
 * 清理 AIService 侧短期记忆的统一接口。
 *
 * <p>AI 会话数据现在分两层：
 * - backend 数据库里保存完整会话和消息历史
 * - AIService 的 Redis 里保存最近几轮短期上下文
 *
 * <p>当用户删除一个会话时，两层都需要同步删除，避免数据库没了但 Redis 还残留旧上下文。
 */
public interface AiConversationMemoryCleaner {
  void clearConversationMemory(String conversationId, Long userId);
}
