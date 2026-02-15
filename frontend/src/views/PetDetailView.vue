<template>
  <div class="pet-screen">
    <header class="pet-screen-top">
      <button type="button" class="back-btn" @click="goHome">返回</button>
      <div class="pet-screen-title">宠物详情</div>
      <div class="pet-screen-top-space"></div>
    </header>

    <main class="pet-screen-main">
      <section class="pet-screen-card">
        <p v-if="loading" class="muted">加载中...</p>
        <p v-else-if="error" class="error">{{ error }}</p>
        <template v-else-if="pet">
          <img class="pet-screen-avatar" :src="pet.avatarUrl || '/assets/images/avatar.jpg'" alt="宠物头像" />
          <div class="pet-screen-name">{{ pet.name || "未命名" }}</div>
          <div class="pet-screen-meta">{{ pet.breed || "未知品种" }} · {{ petGender }}</div>
          <div class="pet-screen-tags">
            <span class="tag">体重 {{ pet.weightKg != null ? `${pet.weightKg}kg` : "--" }}</span>
            <span class="tag">生日 {{ pet.birthday || "未知" }}</span>
          </div>
        </template>
      </section>

      <section class="pet-screen-posts">
        <div class="pet-screen-posts-head">
          <h3>宠物相关日记</h3>
          <button type="button" class="plus-btn" aria-label="发布">+</button>
        </div>
        <div class="pet-screen-empty">
          <p>还未发现宠物相关日记，去添加</p>
        </div>
      </section>
    </main>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { api } from "../utils/api";

const route = useRoute();
const router = useRouter();
const pet = ref(null);
const loading = ref(true);
const error = ref("");

const petGender = computed(() => {
  if (!pet.value) return "未知";
  if (pet.value.gender === 1) return "公";
  if (pet.value.gender === 2) return "母";
  return "未知";
});

onMounted(async () => {
  try {
    const id = route.params.id;
    pet.value = await api.get(`/api/pets/${id}`);
  } catch (err) {
    error.value = err.details?.[0] || err.message || "加载失败";
  } finally {
    loading.value = false;
  }
});

const goHome = () => {
  router.push("/");
};
</script>
