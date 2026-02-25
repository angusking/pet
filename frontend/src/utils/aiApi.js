import { api } from "./api";

export const aiApi = {
  listChats() {
    return api.get("/api/ai/chats");
  },
  createChat(payload = {}) {
    return api.post("/api/ai/chats", payload);
  },
  listMessages(sessionId) {
    return api.get(`/api/ai/chats/${sessionId}/messages`);
  },
  updateSessionPet(sessionId, petId) {
    return api.post(`/api/ai/chats/${sessionId}/pet`, { petId });
  },
  sendMessage(sessionId, content) {
    return api.post(`/api/ai/chats/${sessionId}/messages`, { content });
  },
};
