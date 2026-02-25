<template>
  <div class="ai-pet-context">
    <template v-if="pet">
      <img :src="pet.avatarUrl || '/assets/images/avatar.jpg'" alt="宠物头像" />
      <div class="main">
        <div class="name">当前宠物：{{ pet.name || "未命名" }}</div>
        <div class="sub">{{ pet.breed || "未知品种" }}</div>
      </div>
      <div class="actions">
        <button type="button" class="ghost-btn" @click="$emit('switch')">切换宠物</button>
        <button type="button" class="ghost-btn" @click="$emit('clear')">清空</button>
      </div>
    </template>
    <template v-else>
      <div class="empty">当前未关联宠物，可直接咨询通用问题</div>
      <div class="actions">
        <button type="button" class="ghost-btn" @click="$emit('switch')">选择宠物</button>
      </div>
    </template>
  </div>
</template>

<script setup>
defineProps({
  pet: { type: Object, default: null },
});

defineEmits(["clear", "switch"]);
</script>
