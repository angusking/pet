<template>
  <form class="ai-composer" @submit.prevent="onSubmit">
    <textarea
      v-model.trim="draft"
      rows="3"
      maxlength="2000"
      :disabled="sending"
      placeholder="输入你想咨询的问题..."
    />
    <div class="foot">
      <span class="muted">{{ draft.length }}/2000</span>
      <button type="submit" class="primary send-btn" :disabled="sending || !draft" :aria-busy="sending">
        <span v-if="!sending">发送</span>
        <span v-else class="spinner" aria-hidden="true"></span>
      </button>
    </div>
  </form>
</template>

<script setup>
import { ref, watch } from "vue";

const props = defineProps({
  modelValue: { type: String, default: "" },
  sending: { type: Boolean, default: false },
});

const emit = defineEmits(["update:modelValue", "submit"]);
const draft = ref(props.modelValue);

watch(
  () => props.modelValue,
  (v) => {
    if (v !== draft.value) {
      draft.value = v;
    }
  }
);

watch(draft, (v) => emit("update:modelValue", v));

const onSubmit = () => {
  if (!draft.value || props.sending) {
    return;
  }
  emit("submit", draft.value);
};
</script>

<style scoped>
.send-btn {
  min-width: 72px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.spinner {
  width: 16px;
  height: 16px;
  border: 2px solid rgba(255, 255, 255, 0.35);
  border-top-color: #fff;
  border-radius: 50%;
  animation: ai-spin 0.8s linear infinite;
}

@keyframes ai-spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
