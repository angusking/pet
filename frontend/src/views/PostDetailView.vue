<template>
  <div class="post-detail-page">
    <div class="post-detail-card auth-card">
      <div class="post-detail-head">
        <button type="button" class="ghost-btn" @click="goBack">返回</button>
        <h1>帖子详情</h1>
        <div></div>
      </div>

      <p v-if="loading" class="muted">加载中...</p>
      <p v-else-if="error" class="error">{{ error }}</p>

      <template v-else-if="post">
        <section class="post-author">
          <img class="post-author-avatar" :src="post.author?.avatarUrl || '/assets/images/avatar.jpg'" alt="作者头像" />
          <div class="post-author-main">
            <div class="post-author-name">{{ post.author?.nickname || post.author?.username || `用户 ${post.userId}` }}</div>
            <div class="post-author-meta">
              <span>{{ formatTime(post.createdAt) }}</span>
              <span v-if="post.city"> · {{ post.city }}</span>
            </div>
          </div>
          <button type="button" class="ghost-btn" @click="openAuthorEntry">作者主页</button>
        </section>

        <button v-if="post.pet" type="button" class="post-pet-entry" @click="goPetDetail(post.pet.id)">
          <img :src="post.pet.avatarUrl || '/assets/images/avatar.jpg'" alt="关联宠物" />
          <div class="pet-entry-main">
            <div class="pet-entry-name">{{ post.pet.name || '未命名宠物' }}</div>
            <div class="pet-entry-sub">{{ post.pet.breed || '未知品种' }}</div>
          </div>
          <span class="pet-entry-arrow">></span>
        </button>

        <div v-if="post.mediaUrls?.length" class="publish-grid post-media-grid">
          <img v-for="(url, idx) in post.mediaUrls" :key="idx" :src="url" alt="帖子图片" />
        </div>

        <p class="post-content">{{ post.content }}</p>
        <div v-if="post.tags?.length" class="tags">{{ post.tags.map((t) => `#${t}`).join(' ') }}</div>

        <section class="post-actions">
          <button type="button" class="action-btn icon-only" :disabled="likeLoading" :title="post.likedByMe ? '取消点赞' : '点赞'" @click="toggleLike">
            <span class="action-icon" :class="{ active: post.likedByMe }" aria-hidden="true">
              <svg viewBox="0 0 24 24" fill="none">
                <path d="M12 21s-6.7-4.35-9.32-8.08C.2 9.38 2.1 5 6.27 5c2.28 0 3.68 1.39 4.43 2.55C11.45 6.39 12.85 5 15.13 5c4.17 0 6.07 4.38 3.59 7.92C18.7 14.25 12 21 12 21z" stroke="currentColor" stroke-width="1.8" stroke-linejoin="round"/>
              </svg>
            </span>
            <span class="action-count">{{ post.likeCount ?? 0 }}</span>
          </button>

          <button type="button" class="action-btn icon-only" title="评论" @click="scrollToComments">
            <span class="action-icon" aria-hidden="true">
              <svg viewBox="0 0 24 24" fill="none">
                <path d="M4 6.5a2.5 2.5 0 0 1 2.5-2.5h11A2.5 2.5 0 0 1 20 6.5v7A2.5 2.5 0 0 1 17.5 16H10l-4.2 3.2c-.66.5-1.8.06-1.8-.77V16.2A2.5 2.5 0 0 1 4 13.5v-7z" stroke="currentColor" stroke-width="1.8" stroke-linejoin="round"/>
              </svg>
            </span>
            <span class="action-count">{{ post.commentCount ?? 0 }}</span>
          </button>

          <button type="button" class="action-btn icon-only" :disabled="likesLoading" title="点赞列表" @click="toggleLikesPanel">
            <span class="action-icon" aria-hidden="true">
              <svg viewBox="0 0 24 24" fill="none">
                <path d="M8 7.5A2.5 2.5 0 1 1 8 2.5a2.5 2.5 0 0 1 0 5Z" stroke="currentColor" stroke-width="1.8" />
                <path d="M16.5 9A2 2 0 1 0 16.5 5a2 2 0 0 0 0 4Z" stroke="currentColor" stroke-width="1.8" />
                <path d="M3.8 19.5c.6-3.1 2.5-4.8 4.9-4.8s4.3 1.7 4.9 4.8" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" />
                <path d="M13.8 18.8c.42-2.12 1.72-3.28 3.38-3.28 1.65 0 2.95 1.16 3.37 3.28" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" />
              </svg>
            </span>
            <span class="action-count">{{ likes.length }}</span>
          </button>

          <button v-if="post.canDelete" type="button" class="action-btn icon-only danger-outline" :disabled="deleting" title="删除帖子" @click="deletePost">
            <span class="action-icon" aria-hidden="true">
              <svg viewBox="0 0 24 24" fill="none">
                <path d="M4 7h16" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" />
                <path d="M9.5 4.5h5l1 2.5h-7l1-2.5Z" stroke="currentColor" stroke-width="1.8" stroke-linejoin="round" />
                <path d="M7.5 7v11a2 2 0 0 0 2 2h5a2 2 0 0 0 2-2V7" stroke="currentColor" stroke-width="1.8" />
                <path d="M10 10v6M14 10v6" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" />
              </svg>
            </span>
            <span class="action-count">{{ deleting ? '...' : '' }}</span>
          </button>
        </section>

        <section v-if="showLikesPanel" class="likes-panel">
          <div class="comment-title">点赞列表</div>
          <p v-if="likesLoading" class="muted">加载点赞列表中...</p>
          <div v-else-if="likes.length" class="like-user-list">
            <article v-for="user in likes" :key="user.id" class="like-user-item">
              <img :src="user.avatarUrl || '/assets/images/avatar.jpg'" alt="点赞用户头像" />
              <div class="like-user-main">
                <div class="like-user-name">{{ user.nickname || user.username || `用户 ${user.id}` }}</div>
                <div class="like-user-sub">@{{ user.username }}</div>
              </div>
            </article>
          </div>
          <p v-else class="muted">还没有人点赞</p>
        </section>

        <section id="comment-section" class="comment-section">
          <div class="comment-title">评论区</div>

          <form class="comment-editor" @submit.prevent="submitComment">
            <textarea v-model.trim="commentInput" rows="3" maxlength="500" placeholder="写点评论..." />
            <div class="comment-editor-foot">
              <span class="muted">{{ commentInput.length }}/500</span>
              <button type="submit" class="primary" :disabled="commentSubmitting">发布评论</button>
            </div>
          </form>

          <p v-if="commentError" class="error">{{ commentError }}</p>
          <p v-if="commentLoading" class="muted">评论加载中...</p>

          <div v-else-if="comments.length" class="comment-list">
            <article v-for="item in comments" :key="item.id" class="comment-item">
              <img class="comment-avatar" :src="item.author?.avatarUrl || '/assets/images/avatar.jpg'" alt="评论用户头像" />
              <div class="comment-main">
                <div class="comment-top">
                  <div class="comment-user">{{ item.author?.nickname || item.author?.username || `用户 ${item.userId}` }}</div>
                  <button
                    v-if="item.userId === currentUserId"
                    type="button"
                    class="link-btn-inline comment-delete-btn"
                    :disabled="deletingCommentId === item.id"
                    @click="deleteComment(item)"
                  >
                    {{ deletingCommentId === item.id ? '删除中...' : '删除' }}
                  </button>
                </div>
                <p class="comment-content">{{ item.content }}</p>
                <div class="comment-time">{{ formatTime(item.createdAt) }}</div>
              </div>
            </article>
          </div>
          <p v-else class="muted">还没有评论，来发表第一条评论</p>
        </section>
      </template>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { api } from '../utils/api';

const route = useRoute();
const router = useRouter();
const post = ref(null);
const comments = ref([]);
const likes = ref([]);
const loading = ref(true);
const commentLoading = ref(false);
const likesLoading = ref(false);
const likeLoading = ref(false);
const commentSubmitting = ref(false);
const deleting = ref(false);
const deletingCommentId = ref(null);
const currentUserId = ref(null);
const showLikesPanel = ref(false);
const error = ref('');
const commentError = ref('');
const commentInput = ref('');

const postId = () => route.params.id;

onMounted(async () => {
  await Promise.all([loadPost(), loadComments(), loadCurrentUser()]);
});

const loadPost = async () => {
  loading.value = true;
  error.value = '';
  try {
    post.value = await api.get(`/api/posts/${postId()}`);
  } catch (err) {
    error.value = err.details?.[0] || err.message || '加载帖子失败';
  } finally {
    loading.value = false;
  }
};

const loadComments = async () => {
  commentLoading.value = true;
  commentError.value = '';
  try {
    comments.value = (await api.get(`/api/posts/${postId()}/comments`)) || [];
  } catch (err) {
    commentError.value = err.details?.[0] || err.message || '加载评论失败';
  } finally {
    commentLoading.value = false;
  }
};

const loadCurrentUser = async () => {
  try {
    const me = await api.get('/api/me/profile');
    currentUserId.value = me?.id ?? null;
  } catch {
    currentUserId.value = null;
  }
};

const loadLikes = async () => {
  likesLoading.value = true;
  try {
    likes.value = (await api.get(`/api/posts/${postId()}/likes`)) || [];
  } catch (err) {
    window.alert(err.details?.[0] || err.message || '加载点赞列表失败');
  } finally {
    likesLoading.value = false;
  }
};

const toggleLike = async () => {
  if (!post.value || likeLoading.value) return;
  likeLoading.value = true;
  try {
    const res = post.value.likedByMe
      ? await api.delete(`/api/posts/${post.value.id}/like`)
      : await api.post(`/api/posts/${post.value.id}/like`, {});
    post.value = {
      ...post.value,
      likedByMe: Boolean(res?.liked),
      likeCount: Number(res?.likeCount ?? post.value.likeCount ?? 0),
    };
    if (showLikesPanel.value) {
      await loadLikes();
    }
  } catch (err) {
    window.alert(err.details?.[0] || err.message || '点赞操作失败');
  } finally {
    likeLoading.value = false;
  }
};

const toggleLikesPanel = async () => {
  showLikesPanel.value = !showLikesPanel.value;
  if (showLikesPanel.value) {
    await loadLikes();
  }
};

const submitComment = async () => {
  if (!post.value || commentSubmitting.value) return;
  if (!commentInput.value) {
    commentError.value = '请输入评论内容';
    return;
  }
  commentSubmitting.value = true;
  commentError.value = '';
  try {
    const created = await api.post(`/api/posts/${post.value.id}/comments`, { content: commentInput.value });
    comments.value = [...comments.value, created];
    post.value = { ...post.value, commentCount: Number(post.value.commentCount || 0) + 1 };
    commentInput.value = '';
  } catch (err) {
    commentError.value = err.details?.[0] || err.message || '发表评论失败';
  } finally {
    commentSubmitting.value = false;
  }
};

const deleteComment = async (item) => {
  if (!item?.id || deletingCommentId.value) return;
  if (!window.confirm('确认删除这条评论吗？')) return;
  deletingCommentId.value = item.id;
  try {
    await api.delete(`/api/posts/${postId()}/comments/${item.id}`);
    comments.value = comments.value.filter((c) => c.id !== item.id);
    if (post.value) {
      post.value = { ...post.value, commentCount: Math.max(0, Number(post.value.commentCount || 0) - 1) };
    }
  } catch (err) {
    window.alert(err.details?.[0] || err.message || '删除评论失败');
  } finally {
    deletingCommentId.value = null;
  }
};

const deletePost = async () => {
  if (!post.value?.id || !post.value.canDelete || deleting.value) return;
  if (!window.confirm('确认删除这条帖子吗？删除后不可恢复。')) return;
  deleting.value = true;
  try {
    await api.delete(`/api/posts/${post.value.id}`);
    router.push('/');
  } catch (err) {
    window.alert(err.details?.[0] || err.message || '删除失败');
  } finally {
    deleting.value = false;
  }
};

const scrollToComments = () => {
  document.getElementById('comment-section')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
};

const openAuthorEntry = () => {
  window.alert('作者主页功能待实现');
};

const goPetDetail = (id) => {
  if (!id) return;
  router.push(`/pets/${id}`);
};

const formatTime = (value) => {
  if (!value) return '';
  const d = new Date(value);
  if (Number.isNaN(d.getTime())) return value;
  return d.toLocaleString();
};

const goBack = () => {
  if (window.history.length > 1) {
    router.back();
    return;
  }
  router.push('/');
};
</script>

<style scoped>
.post-detail-page {
  padding: 12px 12px 96px;
}

.post-detail-card {
  width: min(720px, 100%);
  margin: 0 auto;
  display: grid;
  gap: 12px;
}

.post-detail-head {
  display: grid;
  grid-template-columns: auto 1fr auto;
  align-items: center;
  gap: 8px;
}

.post-detail-head h1 {
  margin: 0;
  text-align: center;
  font-size: 18px;
}

.post-author {
  display: grid;
  grid-template-columns: 44px 1fr auto;
  align-items: center;
  gap: 10px;
}

.post-author-avatar {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  object-fit: cover;
  border: 2px solid #f2d2a6;
}

.post-author-name {
  font-weight: 700;
}

.post-author-meta {
  color: var(--muted);
  font-size: 12px;
}

.post-pet-entry {
  display: grid;
  grid-template-columns: 40px 1fr auto;
  align-items: center;
  gap: 10px;
  padding: 10px;
  border-radius: 12px;
  background: #fff7ea;
  border: 1px solid #efdfc8;
  text-align: left;
}

.post-pet-entry img {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  object-fit: cover;
}

.pet-entry-name {
  font-size: 14px;
  font-weight: 700;
}

.pet-entry-sub {
  font-size: 12px;
  color: var(--muted);
}

.pet-entry-arrow {
  color: var(--muted);
}

.post-media-grid {
  margin-top: 0;
}

.post-content {
  margin: 0;
  white-space: pre-wrap;
  line-height: 1.6;
}

.post-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: nowrap;
  overflow-x: auto;
  padding-bottom: 2px;
  -webkit-overflow-scrolling: touch;
  scrollbar-width: none;
}

.post-actions::-webkit-scrollbar {
  display: none;
}

.action-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  min-width: 0;
  border: 1px solid #ead7c1;
  background: #fffaf2;
  border-radius: 999px;
  padding: 9px 10px;
  color: var(--muted);
  font-size: 13px;
  line-height: 1;
  flex: 0 0 auto;
}

.action-btn.icon-only {
  min-width: 56px;
  padding: 9px 12px;
}

.action-btn:disabled {
  opacity: 0.7;
}

.action-icon {
  width: 16px;
  height: 16px;
  display: inline-grid;
  place-items: center;
  flex: 0 0 16px;
}

.action-icon svg {
  width: 16px;
  height: 16px;
  display: block;
}

.action-icon.active {
  color: #e66a7a;
}

.action-count {
  font-size: 12px;
  min-width: 10px;
  text-align: center;
}

.danger-outline {
  color: #b03d3d;
  border-color: #f0c7c7;
}

.likes-panel {
  display: grid;
  gap: 10px;
  padding: 10px;
  border-radius: 12px;
  background: #fffaf2;
  border: 1px solid #efdfc8;
}

.like-user-list {
  display: grid;
  gap: 8px;
}

.like-user-item {
  display: grid;
  grid-template-columns: 36px 1fr;
  gap: 10px;
  align-items: center;
}

.like-user-item img {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  object-fit: cover;
}

.like-user-name {
  font-size: 13px;
  font-weight: 700;
}

.like-user-sub {
  font-size: 12px;
  color: var(--muted);
}

.comment-section {
  padding-top: 6px;
  display: grid;
  gap: 10px;
}

.comment-title {
  font-size: 15px;
  font-weight: 700;
}

.comment-editor {
  display: grid;
  gap: 8px;
}

.comment-editor-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.comment-list {
  display: grid;
  gap: 10px;
}

.comment-item {
  display: grid;
  grid-template-columns: 36px 1fr;
  gap: 10px;
  padding: 10px;
  border-radius: 12px;
  background: #fffaf2;
  border: 1px solid #efdfc8;
}

.comment-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  object-fit: cover;
}

.comment-user {
  font-size: 13px;
  font-weight: 700;
}

.comment-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.comment-delete-btn {
  font-size: 12px;
  color: #b03d3d;
}

.comment-content {
  margin: 4px 0 0;
  line-height: 1.5;
}

.comment-time {
  margin-top: 6px;
  font-size: 12px;
  color: var(--muted);
}

@media (max-width: 420px) {
  .post-author {
    grid-template-columns: 44px 1fr;
  }

  .post-author .ghost-btn {
    grid-column: 1 / -1;
    justify-self: start;
  }

  .post-actions {
    gap: 6px;
  }
}
</style>
