<template>
  <div class="ai-msg-list">
    <p v-if="loading" class="muted">加载对话中...</p>
    <p v-else-if="error" class="error">{{ error }}</p>
    <div v-else-if="messages.length" class="list">
      <AiMessageItem
        v-for="m in messages"
        :key="m.id"
        :message="m"
        @pick-suggestion="$emit('pick-suggestion', $event)"
      />
    </div>
    <div v-else class="ai-empty">
      <p>还没有消息，试试这些问题：</p>
      <div class="suggestions">
        <button type="button" class="pill" @click="$emit('pick-suggestion', '我家宠物今天不爱吃饭怎么办？')">
          不爱吃饭
        </button>
        <button type="button" class="pill" @click="$emit('pick-suggestion', '幼宠驱虫多久一次比较合适？')">
          驱虫频率
        </button>
        <button type="button" class="pill" @click="$emit('pick-suggestion', '最近掉毛很多要注意什么？')">
          掉毛问题
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import AiMessageItem from "./AiMessageItem.vue";

defineProps({
  messages: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  error: { type: String, default: "" },
});

defineEmits(["pick-suggestion"]);
</script>
