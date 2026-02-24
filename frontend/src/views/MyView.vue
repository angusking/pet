<template>
  <div class="my-page">
    <section class="my-identity card-lite">
      <div class="my-user-row">
        <img class="my-avatar" :src="profile.avatarUrl || '/assets/images/avatar.jpg'" alt="用户头像" />
        <div class="my-user-main">
          <div class="my-name">{{ profile.nickname || profile.username || "用户" }}</div>
          <div class="my-sub">{{ profile.city || "欢迎来到宠物社区" }}</div>
        </div>
        <button class="gear-btn" type="button" @click="toastPlaceholder">⚙</button>
      </div>

      <div class="my-current-pet" @click="goPetDetail">
        <img class="my-current-pet-avatar" :src="currentPet?.avatarUrl || '/assets/images/avatar.jpg'" alt="当前宠物" />
        <div class="my-current-pet-main">
          <div class="line-1">{{ currentPet?.name || "未关联宠物" }}</div>
          <div class="line-2">{{ currentPetInfo }}</div>
        </div>
        <button class="link-btn-inline" type="button" @click.stop="switchOpen = !switchOpen">切换宠物</button>
      </div>

      <div v-if="switchOpen" class="my-switch-list">
        <button v-for="pet in pets" :key="pet.id" type="button" class="my-switch-item" @click="switchPet(pet)">
          {{ pet.name }} {{ pet.id === currentPet?.id ? "(当前)" : "" }}
        </button>
      </div>
    </section>

    <section class="my-quick card-lite">
      <button type="button" class="quick-item" @click="scrollToPetManage">我的宠物</button>
      <button type="button" class="quick-item" @click="goMyPosts">我的帖子</button>
      <button type="button" class="quick-item" @click="toastPlaceholder">AI助手记录</button>
      <button type="button" class="quick-item" @click="toastPlaceholder">收藏/点赞</button>
    </section>

    <section id="pet-manage" class="my-pets card-lite">
      <div class="section-title">宠物管理</div>
      <div class="pet-manage-list">
        <button v-for="pet in pets" :key="pet.id" class="pet-manage-card" @click="goPetDetailById(pet.id)">
          <img :src="pet.avatarUrl || '/assets/images/avatar.jpg'" alt="宠物头像" />
          <div class="name">{{ pet.name }}</div>
          <div class="sub">{{ pet.breed || "未知品种" }} · {{ pet.weightKg != null ? `${pet.weightKg}kg` : "--kg" }}</div>
          <div class="tags">{{ pet.tags?.[0] || "健康正常" }}</div>
        </button>
        <button class="pet-manage-add" @click="goAddPet">+ 添加宠物</button>
      </div>
    </section>

    <section class="my-settings card-lite">
      <button class="setting-item" @click="toastPlaceholder">账号与安全</button>
      <button class="setting-item" @click="toastPlaceholder">通知设置</button>
      <button class="setting-item" @click="toastPlaceholder">意见反馈</button>
      <button class="setting-item" @click="toastPlaceholder">关于（v0.1.0）</button>
      <button class="setting-item danger" @click="logout">退出登录</button>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from "vue";
import { useRouter } from "vue-router";
import { api } from "../utils/api";
import { clearToken } from "../utils/auth";

const router = useRouter();
const profile = reactive({ id: null, username: "", nickname: "", avatarUrl: "", city: "" });
const currentPet = ref(null);
const pets = ref([]);
const switchOpen = ref(false);

const currentPetInfo = computed(() => {
  if (!currentPet.value) return "点击添加宠物";
  return `${currentPet.value.breed || "未知品种"} · ${currentPet.value.birthday || "生日未知"}`;
});

onMounted(async () => {
  try {
    const [me, pet, petList] = await Promise.all([
      api.get("/api/me/profile"),
      api.get("/api/me/pet"),
      api.get("/api/me/pets"),
    ]);
    Object.assign(profile, me || {});
    currentPet.value = pet;
    pets.value = Array.isArray(petList) ? petList : [];
  } catch {
    clearToken();
    router.push("/login");
  }
});

const switchPet = async (pet) => {
  try {
    await api.post(`/api/pets/${pet.id}/primary`, {});
    currentPet.value = pet;
    switchOpen.value = false;
  } catch {}
};

const goPetDetail = () => {
  if (!currentPet.value?.id) return;
  router.push(`/pets/${currentPet.value.id}`);
};

const goPetDetailById = (id) => router.push(`/pets/${id}`);
const goAddPet = () => router.push("/pets/create");
const goMyPosts = () => router.push("/me/posts");
const scrollToPetManage = () => document.getElementById("pet-manage")?.scrollIntoView({ behavior: "smooth" });
const toastPlaceholder = () => window.alert("MVP阶段，功能占位");

const logout = () => {
  clearToken();
  router.push("/login");
};
</script>
