<template>
  <div class="auth-page">
    <div class="auth-card post-create-card">
      <h1>发布动态</h1>
      <p class="muted">支持文字、城市和标签，发布后显示在首页信息流</p>

      <form @submit.prevent="submit">
        <div class="muted">关联宠物：{{ linkedPetText }}</div>

        <label>
          内容（必填）
          <textarea v-model.trim="form.content" rows="5" placeholder="说点什么吧..." />
        </label>

        <label>
          城市
          <select v-model="form.city">
            <option value="">不选择</option>
            <option v-for="c in cityOptions" :key="c" :value="c">{{ c }}</option>
          </select>
        </label>

        <label>
          标签
          <div class="tag-editor">
            <input v-model.trim="tagInput" type="text" placeholder="输入标签后回车，例如：遛狗" @keydown.enter.prevent="addTag" />
            <button type="button" class="pill" @click="addTag">添加</button>
          </div>
          <div v-if="form.tags.length" class="tag-row">
            <button v-for="tag in form.tags" :key="tag" type="button" class="tag-removable" @click="removeTag(tag)">
              #{{ tag }} ×
            </button>
          </div>
        </label>

        <div class="avatar-block">
          <div class="avatar-label">图片（可选，最多 9 张）</div>
          <input ref="fileInputRef" class="hidden-file" type="file" accept="image/*" multiple @change="onFilesChange" />
          <button type="button" class="ghost-btn form-btn" @click="chooseImages">选择图片</button>
          <div v-if="previewUrls.length" class="publish-grid">
            <img v-for="(url, idx) in previewUrls" :key="idx" :src="url" alt="预览图" />
          </div>
          <p v-if="uploading" class="muted">上传图片中...</p>
        </div>

        <p v-if="error" class="error">{{ error }}</p>
        <button type="submit" class="primary full" :disabled="uploading">发布</button>
        <button type="button" class="ghost-btn form-btn" :disabled="uploading" @click="goBack">取消</button>
      </form>
    </div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { api } from "../utils/api";

const route = useRoute();
const router = useRouter();
const fileInputRef = ref(null);
const selectedFiles = ref([]);
const previewUrls = ref([]);
const uploading = ref(false);
const error = ref("");
const linkedPet = ref(null);
const tagInput = ref("");
const MAX_UPLOAD_MB = Number(import.meta.env.VITE_MAX_UPLOAD_MB || 20);
const MAX_UPLOAD_BYTES = MAX_UPLOAD_MB * 1024 * 1024;

const cityOptions = ["北京", "上海", "广州", "深圳", "杭州", "成都", "重庆", "武汉", "南京", "西安"];

const form = reactive({
  petId: null,
  content: "",
  city: "",
  tags: [],
});

const linkedPetText = computed(() => {
  if (!form.petId) return "未关联宠物";
  return linkedPet.value?.name ? linkedPet.value.name : `宠物 #${form.petId}`;
});

onMounted(async () => {
  const petId = route.query.petId ? Number(route.query.petId) : null;
  if (petId && Number.isFinite(petId)) {
    form.petId = petId;
    try {
      linkedPet.value = await api.get(`/api/pets/${petId}`);
    } catch {}
  }
});

onBeforeUnmount(() => {
  previewUrls.value.forEach((u) => URL.revokeObjectURL(u));
});

const addTag = () => {
  const v = tagInput.value.trim().replace(/^#/, "");
  if (!v) return;
  if (form.tags.includes(v)) {
    tagInput.value = "";
    return;
  }
  if (form.tags.length >= 8) {
    error.value = "最多添加 8 个标签";
    return;
  }
  form.tags.push(v);
  tagInput.value = "";
  error.value = "";
};

const removeTag = (tag) => {
  form.tags = form.tags.filter((t) => t !== tag);
};

const chooseImages = () => fileInputRef.value?.click();

const messageFromError = (err, fallback) => {
  const detail = Array.isArray(err?.details) && err.details.length > 0 ? err.details[0] : "";
  if (detail) return detail;
  if (err?.message) return err.message;
  return fallback;
};

const onFilesChange = (event) => {
  previewUrls.value.forEach((u) => URL.revokeObjectURL(u));
  const picked = Array.from(event.target.files || []);
  if (!picked.length) {
    selectedFiles.value = [];
    previewUrls.value = [];
    return;
  }

  if (picked.some((f) => !f.type.startsWith("image/"))) {
    error.value = "仅支持图片格式文件";
    selectedFiles.value = [];
    previewUrls.value = [];
    return;
  }
  const tooLarge = picked.find((f) => f.size > MAX_UPLOAD_BYTES);
  if (tooLarge) {
    error.value = `图片过大，请上传 ${MAX_UPLOAD_MB}MB 以内文件`;
    selectedFiles.value = [];
    previewUrls.value = [];
    return;
  }

  const files = picked.slice(0, 9);
  if (picked.length > 9) {
    error.value = "最多上传 9 张图片";
  }
  selectedFiles.value = files;
  previewUrls.value = files.map((f) => URL.createObjectURL(f));
  if (picked.length <= 9) {
    error.value = "";
  }
};

const submit = async () => {
  if (!form.content) {
    error.value = "请输入发布内容";
    return;
  }

  uploading.value = true;
  error.value = "";
  try {
    const mediaUrls = [];
    for (const file of selectedFiles.value) {
      const uploaded = await api.upload("/api/uploads/post", file);
      mediaUrls.push(uploaded.path);
    }

    await api.post("/api/posts", {
      petId: form.petId,
      content: form.content,
      city: form.city || null,
      tags: form.tags,
      mediaUrls,
    });
    router.push("/");
  } catch (err) {
    error.value = messageFromError(err, "发布失败，请稍后重试");
  } finally {
    uploading.value = false;
  }
};

const goBack = () => router.push("/");
</script>
