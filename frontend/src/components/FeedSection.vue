<template>
  <main class="feed">
    <div class="feed-head">
      <div class="feed-title">{{ feedTitle }}</div>
      <div class="feed-actions">
        <button class="pill" @click="$emit('refresh')">刷新</button>
      </div>
    </div>

    <div v-if="loading" class="muted">加载中...</div>
    <p v-else-if="error" class="error">{{ error }}</p>
    <p v-else-if="!posts.length" class="muted">还没有动态，去发布第一条吧。</p>

    <section v-else class="masonry">
      <article v-for="post in posts" :key="post.id" class="card clickable" @click="openPost(post.id)">
        <div class="card-head">
          <span class="user">用户 {{ post.userId }}{{ post.city ? ` · ${post.city}` : "" }}</span>
        </div>

        <div v-if="post.mediaUrls?.length" class="media" :class="{ tall: post.mediaUrls.length > 1 }">
          <img :src="post.mediaUrls[0]" alt="帖子图片" />
          <span class="media-badge">📷 {{ post.mediaUrls.length }}</span>
        </div>

        <h3>{{ trimContent(post.content) }}</h3>
        <p class="excerpt">{{ post.content }}</p>
        <div v-if="post.tags?.length" class="tags">{{ post.tags.map((t) => `#${t}`).join(" ") }}</div>
        <div class="meta">{{ formatTime(post.createdAt) }}</div>
      </article>
    </section>
  </main>
</template>

<script setup>
import { computed } from "vue";
import { useRouter } from "vue-router";

const props = defineProps({
  feedType: { type: String, default: "discover" },
  posts: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  error: { type: String, default: "" },
});

defineEmits(["refresh"]);
const router = useRouter();

const feedTitle = computed(() => (props.feedType === "follow" ? "关注动态" : "发现动态"));

const trimContent = (content) => {
  if (!content) return "动态";
  return content.length > 18 ? `${content.slice(0, 18)}...` : content;
};

const formatTime = (v) => {
  if (!v) return "";
  const d = new Date(v);
  if (Number.isNaN(d.getTime())) return v;
  return d.toLocaleString();
};

const openPost = (id) => {
  if (!id) return;
  router.push(`/posts/${id}`);
};
</script>
