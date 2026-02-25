<template>
  <article class="ai-msg" :class="`role-${message.role}`">
    <div class="bubble">
      <template v-if="parsedAssistant">
        <div class="ai-structured">
          <div class="ai-structured-head">
            <span class="risk-pill" :class="`risk-${riskUi.key}`">{{ riskUi.label }}</span>
            <span class="intent-pill">{{ intentUi }}</span>
          </div>

          <div class="content structured-answer">{{ parsedAssistant.answer || "暂无回答" }}</div>

          <div
            v-if="parsedAssistant.actionCards?.length"
            class="action-card-list"
          >
            <section
              v-for="(card, idx) in parsedAssistant.actionCards"
              :key="`${idx}-${card.title || 'card'}`"
              class="action-card"
            >
              <div class="action-card-title">{{ card.title || "建议卡片" }}</div>
              <ul v-if="Array.isArray(card.items) && card.items.length" class="action-card-items">
                <li v-for="(item, itemIdx) in card.items" :key="`${idx}-${itemIdx}`">{{ item }}</li>
              </ul>
            </section>
          </div>

          <div
            v-if="parsedAssistant.followUpQuestions?.length"
            class="follow-up-list"
          >
            <button
              v-for="(q, idx) in parsedAssistant.followUpQuestions"
              :key="`${idx}-${q}`"
              type="button"
              class="pill"
              @click="$emit('pick-suggestion', q)"
            >
              {{ q }}
            </button>
          </div>

          <p v-if="parsedAssistant.disclaimer" class="ai-disclaimer">
            {{ parsedAssistant.disclaimer }}
          </p>
        </div>
      </template>

      <template v-else>
        <div class="content">{{ message.content }}</div>
      </template>

      <div class="meta">
        <span>{{ message.role === "assistant" ? "AI" : "我" }}</span>
        <span>{{ formatTime(message.createdAt) }}</span>
      </div>
    </div>
  </article>
</template>

<script setup>
import { computed } from "vue";

const props = defineProps({
  message: { type: Object, required: true },
});

defineEmits(["pick-suggestion"]);

const parsedAssistant = computed(() => {
  if (props.message?.role !== "assistant" || !props.message?.content) {
    return null;
  }
  const raw = String(props.message.content).trim();
  const jsonText = extractJson(raw);
  if (!jsonText) return null;
  try {
    const obj = JSON.parse(jsonText);
    if (!obj || typeof obj !== "object") return null;
    return {
      intent: typeof obj.intent === "string" ? obj.intent : "UNKNOWN",
      riskLevel: typeof obj.riskLevel === "string" ? obj.riskLevel : "NONE",
      answer: typeof obj.answer === "string" ? obj.answer : "",
      actionCards: Array.isArray(obj.actionCards) ? obj.actionCards : [],
      followUpQuestions: Array.isArray(obj.followUpQuestions) ? obj.followUpQuestions : [],
      disclaimer: typeof obj.disclaimer === "string" ? obj.disclaimer : "",
    };
  } catch {
    return null;
  }
});

const riskUi = computed(() => {
  const risk = (parsedAssistant.value?.riskLevel || "NONE").toUpperCase();
  const map = {
    HIGH: { key: "high", label: "建议尽快就医" },
    MEDIUM: { key: "medium", label: "需观察" },
    LOW: { key: "low", label: "轻度关注" },
    NONE: { key: "none", label: "通用建议" },
  };
  return map[risk] || map.NONE;
});

const intentUi = computed(() => {
  const intent = (parsedAssistant.value?.intent || "UNKNOWN").toUpperCase();
  const map = {
    HEALTH: "健康",
    CARE: "护理",
    TRAINING: "训练",
    FEEDING: "喂养",
    TRAVEL: "出行",
    COMMUNITY: "社区",
    CHITCHAT: "聊天",
    UNKNOWN: "未分类",
  };
  return map[intent] || "未分类";
});

function extractJson(text) {
  if (!text) return null;
  if (text.startsWith("{") && text.endsWith("}")) {
    return text;
  }
  const fenced = text.match(/```json\s*([\s\S]*?)\s*```/i) || text.match(/```\s*([\s\S]*?)\s*```/i);
  if (fenced?.[1]) {
    return fenced[1].trim();
  }
  const start = text.indexOf("{");
  const end = text.lastIndexOf("}");
  if (start >= 0 && end > start) {
    return text.slice(start, end + 1);
  }
  return null;
}

function formatTime(value) {
  if (!value) return "";
  const d = new Date(value);
  if (Number.isNaN(d.getTime())) return value;
  return d.toLocaleTimeString();
}
</script>
