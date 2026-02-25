package com.pet.api.ai.dto;

public record AiChatSendMessageResponse(
    AiChatSessionResponse session,
    AiChatMessageResponse userMessage,
    AiChatMessageResponse assistantMessage
) {}
