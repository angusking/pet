<template>
  <div class="pet-screen">
    <header class="pet-screen-top">
      <button type="button" class="back-btn" @click="goHome">返回</button>
      <div class="pet-screen-title">宠物详情</div>
      <div class="pet-screen-top-space"></div>
    </header>

    <main class="pet-screen-main">
      <section class="pet-screen-card">
        <p v-if="loadingPet" class="muted">加载中...</p>
        <p v-else-if="petError" class="error">{{ petError }}</p>
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

        <p v-if="loadingPosts" class="muted">日记加载中...</p>
        <p v-else-if="postsError" class="error">{{ postsError }}</p>
        <div v-else-if="posts.length" class="pet-diary-list">
          <article v-for="post in posts" :key="post.id" class="pet-diary-item" @click="goPostDetail(post.id)">
            <p class="pet-diary-content">{{ post.content }}</p>
            <div v-if="post.tags && post.tags.length" class="tags">{{ post.tags.map((t) => `#${t}`).join(" ") }}</div>
            <img
              v-if="post.mediaUrls && post.mediaUrls.length"
              class="pet-diary-img"
              :src="post.mediaUrls[0]"
              alt="日记图片"
            />
            <div class="pet-diary-meta">{{ formatTime(post.createdAt) }}</div>
          </article>
        </div>
        <div v-else class="pet-screen-empty">
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
const posts = ref([]);
const loadingPet = ref(true);
const loadingPosts = ref(true);
const petError = ref("");
const postsError = ref("");

const petGender = computed(() => {
  if (!pet.value) return "未知";
  if (pet.value.gender === 1) return "公";
  if (pet.value.gender === 2) return "母";
  return "未知";
});

onMounted(async () => {
  const id = route.params.id;
  try {
    pet.value = await api.get(`/api/pets/${id}`);
  } catch (err) {
    petError.value = err.details?.[0] || err.message || "加载失败";
  } finally {
    loadingPet.value = false;
  }

  try {
    const page = await api.get(`/api/posts?petId=${id}&page=0&size=20`);
    posts.value = page?.content || [];
  } catch (err) {
    postsError.value = err.details?.[0] || err.message || "加载日记失败";
  } finally {
    loadingPosts.value = false;
  }
});

const formatTime = (value) => {
  if (!value) return "";
  const d = new Date(value);
  if (Number.isNaN(d.getTime())) return value;
  return d.toLocaleString();
};

const goHome = () => {
  router.push("/");
};

const goPostDetail = (id) => {
  if (!id) return;
  router.push(`/posts/${id}`);
};
</script>
