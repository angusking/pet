<template>
  <header class="ai-chat-header">
    <div class="ai-chat-header-left">
      <button
        type="button"
        class="icon-btn history-toggle"
        aria-label="打开对话历史"
        @click="$emit('toggle-history')"
      >
        <span></span>
        <span></span>
        <span></span>
      </button>
      <div class="ai-chat-title">AI助手</div>
    </div>
    <button type="button" class="ghost-btn" @click="$emit('new-chat')">新建对话</button>
  </header>
</template>

<script setup>
defineEmits(["new-chat", "toggle-history"]);
</script>
