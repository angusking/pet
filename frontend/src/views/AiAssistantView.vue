<template>
  <div class="ai-page">
    <div class="ai-layout">
      <AiChatHeader @new-chat="createNewChat" />
      <PetContextBar :pet="petContext" @clear="clearPetContext" @switch="togglePetPicker" />

      <section v-if="petPickerOpen" class="ai-pet-picker">
        <div class="picker-head">
          <strong>切换当前宠物</strong>
          <button type="button" class="ghost-btn" @click="petPickerOpen = false">关闭</button>
        </div>
        <div class="picker-list">
          <button
            type="button"
            class="picker-item"
            :class="{ active: !petContext?.id }"
            :disabled="petSwitching"
            @click="selectPet(null)"
          >
            <span class="name">不关联宠物</span>
            <span class="sub">当前对话按通用问题处理</span>
          </button>
          <button
            v-for="pet in myPets"
            :key="pet.id"
            type="button"
            class="picker-item"
            :class="{ active: petContext?.id === pet.id }"
            :disabled="petSwitching"
            @click="selectPet(pet.id)"
          >
            <span class="name">{{ pet.name || "未命名" }}</span>
            <span class="sub">{{ pet.breed || "未知品种" }}</span>
          </button>
        </div>
        <p v-if="petSwitchError" class="error">{{ petSwitchError }}</p>
      </section>

      <section class="ai-chat-shell">
        <aside class="ai-session-list">
          <div class="session-title">对话历史</div>
          <button
            v-for="s in sessions"
            :key="s.id"
            type="button"
            class="session-item"
            :class="{ active: currentSessionId === s.id }"
            @click="selectSession(s.id)"
          >
            <div class="name">{{ s.title || "新对话" }}</div>
            <div class="sub">{{ s.lastMessagePreview || "暂无消息" }}</div>
          </button>
          <p v-if="!sessions.length" class="muted">暂无会话</p>
        </aside>

        <section class="ai-main-panel">
          <AiMessageList
            :messages="messages"
            :loading="messageLoading"
            :error="messageError"
            @pick-suggestion="useSuggestion"
          />
          <p v-if="sendError" class="error">{{ sendError }}</p>
          <AiComposer v-model="draft" :sending="sending" @submit="send" />
        </section>
      </section>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from "vue";
import { aiApi } from "../utils/aiApi";
import { api } from "../utils/api";
import AiChatHeader from "../components/ai/AiChatHeader.vue";
import AiComposer from "../components/ai/AiComposer.vue";
import AiMessageList from "../components/ai/AiMessageList.vue";
import PetContextBar from "../components/ai/PetContextBar.vue";

const petContext = ref(null);
const myPets = ref([]);
const petPickerOpen = ref(false);
const petSwitching = ref(false);
const petSwitchError = ref("");
const sessions = ref([]);
const currentSessionId = ref(null);
const messages = ref([]);
const draft = ref("");
const sending = ref(false);
const messageLoading = ref(false);
const messageError = ref("");
const sendError = ref("");

onMounted(async () => {
  await Promise.all([loadCurrentPet(), loadMyPets(), loadSessions()]);
  if (!sessions.value.length) {
    await createNewChat();
  } else if (sessions.value[0]?.id) {
    await selectSession(sessions.value[0].id);
  }
});

const loadCurrentPet = async () => {
  try {
    petContext.value = await api.get("/api/me/pet");
  } catch {
    petContext.value = null;
  }
};

const loadMyPets = async () => {
  try {
    myPets.value = (await api.get("/api/me/pets")) || [];
  } catch {
    myPets.value = [];
  }
};

const loadSessions = async () => {
  sessions.value = (await aiApi.listChats()) || [];
};

const createNewChat = async () => {
  const session = await aiApi.createChat({
    petId: petContext.value?.id ?? null,
    title: "",
  });
  await loadSessions();
  currentSessionId.value = session.id;
  messages.value = [];
};

const selectSession = async (sessionId) => {
  if (!sessionId) return;
  currentSessionId.value = sessionId;
  messageLoading.value = true;
  messageError.value = "";
  try {
    messages.value = (await aiApi.listMessages(sessionId)) || [];
  } catch (err) {
    messageError.value = err.details?.[0] || err.message || "加载消息失败";
  } finally {
    messageLoading.value = false;
  }
};

const send = async (text) => {
  if (!text || sending.value) return;
  sending.value = true;
  sendError.value = "";
  if (!currentSessionId.value) {
    try {
      await createNewChat();
    } catch (err) {
      sendError.value = err.details?.[0] || err.message || "创建会话失败";
      sending.value = false;
      return;
    }
  }
  try {
    const resp = await aiApi.sendMessage(currentSessionId.value, text);
    messages.value = [...messages.value, resp.userMessage, resp.assistantMessage];
    draft.value = "";
    await loadSessions();
  } catch (err) {
    sendError.value = err.details?.[0] || err.message || "发送失败";
  } finally {
    sending.value = false;
  }
};

const useSuggestion = (text) => {
  draft.value = text;
};

const togglePetPicker = () => {
  petSwitchError.value = "";
  petPickerOpen.value = !petPickerOpen.value;
};

const clearPetContext = async () => {
  petContext.value = null;
  petSwitchError.value = "";
  if (!currentSessionId.value) {
    return;
  }
  try {
    await aiApi.updateSessionPet(currentSessionId.value, null);
    await loadSessions();
  } catch (err) {
    petSwitchError.value = err.details?.[0] || err.message || "更新对话宠物失败";
  }
};

const selectPet = async (petId) => {
  if (petSwitching.value) return;
  petSwitching.value = true;
  petSwitchError.value = "";
  try {
    if (petId) {
      await api.post(`/api/pets/${petId}/primary`, {});
      await loadCurrentPet();
    } else {
      petContext.value = null;
    }
    if (currentSessionId.value) {
      await aiApi.updateSessionPet(currentSessionId.value, petId);
      await loadSessions();
    }
    petPickerOpen.value = false;
  } catch (err) {
    petSwitchError.value = err.details?.[0] || err.message || "切换宠物失败";
  } finally {
    petSwitching.value = false;
  }
};
</script>
