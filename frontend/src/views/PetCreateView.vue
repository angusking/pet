<template>
  <div class="auth-page">
    <div class="auth-card">
      <h1>添加宠物</h1>
      <p class="muted">先选择头像并填写信息，点击保存后再提交到后台</p>

      <form @submit.prevent="submit">
        <div class="avatar-block">
          <div class="avatar-label">宠物头像（必选）</div>
          <img class="create-avatar-preview" :src="avatarPreview" alt="宠物头像预览" />
          <input ref="fileInputRef" class="hidden-file" type="file" accept="image/*" @change="onFileChange" />
          <button type="button" class="ghost-btn form-btn" @click="chooseImage">从相册选择图片</button>
          <p v-if="submitting" class="muted">正在提交，请稍候...</p>
          <p v-else-if="selectedFile" class="muted">图片已本地缓存，点击“保存”后上传</p>
        </div>

        <label>
          宠物名称（必填）
          <input v-model.trim="form.name" type="text" placeholder="例如：小麦 / 豆豆 / Lucky" />
        </label>

        <label>
          宠物品种（建议填写）
          <input v-model.trim="form.breed" type="text" placeholder="例如：金毛、英短" />
        </label>

        <label>
          性别
          <select v-model.number="form.gender">
            <option :value="0">未知</option>
            <option :value="1">公</option>
            <option :value="2">母</option>
          </select>
        </label>

        <label>
          出生日期（建议填写）
          <input v-model="form.birthday" type="date" />
        </label>

        <label>
          当前体重 KG（建议填写）
          <input v-model.trim="form.weightKg" type="number" step="0.1" min="0" placeholder="例如：12.5" />
        </label>

        <p class="muted">第一只宠物会自动设为主宠物，后续可在宠物列表切换。</p>
        <p v-if="error" class="error">{{ error }}</p>

        <button type="submit" class="primary full" :disabled="submitting">保存</button>
        <button type="button" class="ghost-btn form-btn" :disabled="submitting" @click="goBack">取消</button>
      </form>
    </div>
  </div>
</template>

<script setup>
import { computed, reactive, ref } from "vue";
import { useRouter } from "vue-router";
import { api } from "../utils/api";

const router = useRouter();
const fileInputRef = ref(null);
const error = ref("");
const submitting = ref(false);
const selectedFile = ref(null);
const localPreviewBase64 = ref("");
const form = reactive({
  name: "",
  breed: "",
  gender: 0,
  birthday: "",
  weightKg: "",
  avatarUrl: "",
  tags: ["健康正常"],
});

const avatarPreview = computed(() => localPreviewBase64.value || form.avatarUrl || "/assets/images/avatar.jpg");

const messageFromError = (err, fallback) => {
  if (err?.status === 401 || err?.code === 1001) return "登录已过期，请重新登录";
  if (err?.code === 3000) return "请选择图片后再上传";
  if (err?.code === 3001) return "图片上传失败，请稍后重试";
  if (err?.code === 3002) return "图片过大，请上传 5MB 以内文件";
  if (err?.code === 1000) return err?.details?.[0] || "请检查表单内容";
  if (Array.isArray(err?.details) && err.details.length > 0) return err.details[0];
  return err?.message || fallback;
};

const readAsBase64 = (file) =>
  new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(reader.result);
    reader.onerror = () => reject(new Error("图片读取失败"));
    reader.readAsDataURL(file);
  });

const chooseImage = () => {
  fileInputRef.value?.click();
};

const onFileChange = async (event) => {
  const file = event.target.files?.[0];
  if (!file) return;

  if (!file.type.startsWith("image/")) {
    error.value = "仅支持图片格式文件";
    return;
  }
  if (file.size > 5 * 1024 * 1024) {
    error.value = "图片大小不能超过 5MB";
    return;
  }

  try {
    localPreviewBase64.value = await readAsBase64(file);
    selectedFile.value = file;
    form.avatarUrl = "";
    error.value = "";
  } catch (err) {
    error.value = err.message || "图片读取失败";
  }
};

const submit = async () => {
  if (!selectedFile.value) {
    error.value = "请先选择宠物头像";
    return;
  }
  if (!form.name) {
    error.value = "请输入宠物名称";
    return;
  }

  error.value = "";
  submitting.value = true;
  try {
    const uploadRes = await api.upload("/api/uploads/pet-avatar", selectedFile.value);
    form.avatarUrl = uploadRes.path;

    await api.post("/api/pets", {
      name: form.name,
      breed: form.breed || null,
      gender: form.gender,
      birthday: form.birthday || null,
      weightKg: form.weightKg === "" ? null : Number(form.weightKg),
      avatarUrl: form.avatarUrl,
      tags: form.tags,
    });
    router.push("/");
  } catch (err) {
    error.value = messageFromError(err, "保存失败，请稍后重试");
  } finally {
    submitting.value = false;
  }
};

const goBack = () => {
  router.push("/");
};
</script>
