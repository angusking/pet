<template>
  <div class="auth-page">
    <div class="auth-card">
      <h1>我的帖子</h1>
      <p class="muted">查看我发布的动态</p>
      <p v-if="loading" class="muted">加载中...</p>
      <p v-else-if="error" class="error">{{ error }}</p>
      <div v-else-if="posts.length" class="pet-diary-list">
        <article v-for="post in posts" :key="post.id" class="pet-diary-item" @click="goPostDetail(post.id)">
          <p class="pet-diary-content">{{ post.content }}</p>
          <div v-if="post.tags?.length" class="tags">{{ post.tags.map((t) => `#${t}`).join(" ") }}</div>
          <img v-if="post.mediaUrls?.length" class="pet-diary-img" :src="post.mediaUrls[0]" alt="帖子图片" />
          <div class="pet-diary-meta">{{ formatTime(post.createdAt) }}</div>
        </article>
      </div>
      <p v-else class="muted">你还没有发布过动态</p>
      <button class="ghost-btn form-btn" @click="goBack">返回我的</button>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { api } from "../utils/api";

const router = useRouter();
const posts = ref([]);
const loading = ref(true);
const error = ref("");

onMounted(async () => {
  try {
    const page = await api.get("/api/me/posts?page=0&size=20");
    posts.value = page?.content || [];
  } catch (err) {
    error.value = err.details?.[0] || err.message || "加载失败";
  } finally {
    loading.value = false;
  }
});

const formatTime = (value) => {
  if (!value) return "";
  const d = new Date(value);
  if (Number.isNaN(d.getTime())) return value;
  return d.toLocaleString();
};

const goBack = () => router.push("/me");
const goPostDetail = (id) => {
  if (!id) return;
  router.push(`/posts/${id}`);
};
</script>
