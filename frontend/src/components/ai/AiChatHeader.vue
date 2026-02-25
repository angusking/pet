<template>
  <header class="ai-chat-header">
    <div class="ai-chat-title">AI助手</div>
    <button type="button" class="ghost-btn" @click="$emit('new-chat')">新建对话</button>
  </header>
</template>

<script setup>
defineEmits(["new-chat"]);
</script>
