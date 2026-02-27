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
      <article
        v-for="post in posts"
        :key="post.id"
        class="card"
        :class="{ clickable: !post.ad, ad: post.ad }"
        @click="openPost(post)"
      >
        <div class="card-head">
          <span class="user">用户 {{ post.userId }}{{ post.city ? ` · ${post.city}` : "" }}</span>
        </div>

        <div v-if="post.mediaUrls?.length" class="media" :class="{ tall: post.mediaUrls.length > 1 }">
          <img
            :src="toFeedImageUrl(post.mediaUrls[0])"
            alt="帖子图片"
            loading="lazy"
            decoding="async"
            @error="onImageError($event, post.mediaUrls[0])"
          />
          <span v-if="!post.ad" class="media-badge">📷 {{ post.mediaUrls.length }}</span>
        </div>

        <h3>{{ post.title || trimContent(post.content) }}</h3>
        <p class="excerpt">{{ post.content }}</p>
        <div v-if="post.tags?.length" class="tags">{{ post.tags.map((t) => `#${t}`).join(" ") }}</div>
        <div class="meta">{{ formatTime(post.createdAt) }}</div>
        <span v-if="post.ad" class="ad-corner">广告</span>
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
  return d.toLocaleString("zh-CN", { timeZone: "Asia/Shanghai" });
};

const openPost = (post) => {
  if (!post || post.ad || !post.id) return;
  router.push(`/posts/${post.id}`);
};

const toFeedImageUrl = (url) => {
  if (!url) return url;
  const q = url.indexOf("?");
  const path = q >= 0 ? url.slice(0, q) : url;
  const query = q >= 0 ? url.slice(q) : "";
  const dot = path.lastIndexOf(".");
  const slash = path.lastIndexOf("/");
  if (dot <= slash) return url;
  return `${path.slice(0, dot)}_thumb.jpg${query}`;
};

const onImageError = (event, originalUrl) => {
  if (!event?.target || !originalUrl) return;
  if (event.target.dataset.fallbackApplied === "1") return;
  event.target.dataset.fallbackApplied = "1";
  event.target.src = originalUrl;
};
</script>
