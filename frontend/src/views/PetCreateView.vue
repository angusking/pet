<template>
  <div class="auth-page">
    <div class="auth-card">
      <h1>添加宠物</h1>
      <p class="muted">先选择头像，再填写基础档案。分类支持最多三级，颜色和体型等特征请写在补充说明里。</p>

      <form @submit.prevent="submit">
        <div class="avatar-block">
          <div class="avatar-label">宠物头像（必选）</div>
          <img class="create-avatar-preview" :src="avatarPreview" alt="宠物头像预览" />
          <input ref="fileInputRef" class="hidden-file" type="file" accept="image/*" @change="onFileChange" />
          <button type="button" class="ghost-btn form-btn" @click="chooseImage">从相册选择图片</button>
          <p v-if="submitting" class="muted">正在提交，请稍候...</p>
          <p v-else-if="selectedFile" class="muted">图片已缓存在本地，点击“保存”后会自动上传</p>
        </div>

        <label>
          宠物名称（必填）
          <input v-model.trim="form.name" type="text" placeholder="例如：小黄 / 豆豆 / Lucky" />
        </label>

        <label>
          一级分类
          <select v-model.number="form.level1Id" @change="onLevel1Change">
            <option :value="null">请选择一级分类</option>
            <option v-for="item in level1Categories" :key="item.id" :value="item.id">{{ item.name }}</option>
          </select>
        </label>

        <label>
          二级分类
          <select v-model.number="form.level2Id" :disabled="!level2Categories.length" @change="onLevel2Change">
            <option :value="null">可停留在一级分类</option>
            <option v-for="item in level2Categories" :key="item.id" :value="item.id">{{ item.name }}</option>
          </select>
        </label>

        <label>
          三级分类
          <select v-model.number="form.level3Id" :disabled="!level3Categories.length">
            <option :value="null">可停留在二级分类</option>
            <option v-for="item in level3Categories" :key="item.id" :value="item.id">{{ item.name }}</option>
          </select>
        </label>

        <label>
          补充说明（可选）
          <input
            v-model.trim="form.customSpeciesNote"
            type="text"
            placeholder="例如：黄化大体 / 蓝化 / 长毛 / 特殊体型"
          />
        </label>

        <p class="muted">
          找不到准确分类时，可以只选到一级或二级，再填写补充说明；也可以只填写补充说明作为兜底。
        </p>

        <label>
          性别
          <select v-model="form.gender">
            <option value="unknown">未知</option>
            <option value="male">公</option>
            <option value="female">母</option>
          </select>
        </label>

        <label>
          出生日期（建议填写）
          <input v-model="form.birthDate" type="date" />
        </label>

        <label>
          当前体重 KG（建议填写）
          <input v-model.trim="form.currentWeight" type="number" step="0.1" min="0" placeholder="例如：12.5" />
        </label>

        <label class="checkbox-row">
          <input v-model="form.neutered" type="checkbox" />
          <span>已绝育 / 已去势</span>
        </label>

        <p class="muted">第一只宠物会自动设为主宠物，后续可在宠物列表里切换。</p>
        <p v-if="categoryError" class="error">{{ categoryError }}</p>
        <p v-if="error" class="error">{{ error }}</p>

        <button type="submit" class="primary full" :disabled="submitting">保存</button>
        <button type="button" class="ghost-btn form-btn" :disabled="submitting" @click="goBack">取消</button>
      </form>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from "vue";
import { useRouter } from "vue-router";
import { api } from "../utils/api";

const router = useRouter();
const fileInputRef = ref(null);
const error = ref("");
const categoryError = ref("");
const submitting = ref(false);
const selectedFile = ref(null);
const localPreviewBase64 = ref("");
const categoryTree = ref([]);
const MAX_UPLOAD_MB = Number(import.meta.env.VITE_MAX_UPLOAD_MB || 20);
const MAX_UPLOAD_BYTES = MAX_UPLOAD_MB * 1024 * 1024;

const form = reactive({
  name: "",
  level1Id: null,
  level2Id: null,
  level3Id: null,
  customSpeciesNote: "",
  gender: "unknown",
  birthDate: "",
  neutered: false,
  currentWeight: "",
  avatarUrl: "",
  tags: ["健康正常"],
});

const avatarPreview = computed(() => localPreviewBase64.value || form.avatarUrl || "/assets/images/avatar.jpg");
const level1Categories = computed(() => categoryTree.value || []);
const level2Categories = computed(() => {
  const level1 = level1Categories.value.find((item) => item.id === form.level1Id);
  return level1?.children || [];
});
const level3Categories = computed(() => {
  const level2 = level2Categories.value.find((item) => item.id === form.level2Id);
  return level2?.children || [];
});

onMounted(async () => {
  try {
    categoryTree.value = (await api.get("/api/pet/categories/tree")) || [];
  } catch (err) {
    categoryTree.value = [];
    categoryError.value = err.details?.[0] || err.message || "加载宠物分类失败";
  }
});

const messageFromError = (err, fallback) => {
  if (err?.status === 401 || err?.code === 1001) return "登录已过期，请重新登录";
  if (err?.code === 3000) return "请选择图片后再上传";
  if (err?.code === 3001) return "图片上传失败，请稍后重试";
  if (err?.code === 3002) return `图片过大，请上传 ${MAX_UPLOAD_MB}MB 以内文件`;
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

const onLevel1Change = () => {
  form.level2Id = null;
  form.level3Id = null;
};

const onLevel2Change = () => {
  form.level3Id = null;
};

const onFileChange = async (event) => {
  const file = event.target.files?.[0];
  if (!file) return;

  if (!file.type.startsWith("image/")) {
    error.value = "仅支持图片格式文件";
    return;
  }
  if (file.size > MAX_UPLOAD_BYTES) {
    error.value = `图片过大，请上传 ${MAX_UPLOAD_MB}MB 以内文件`;
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

const selectedCategoryId = () => form.level3Id || form.level2Id || form.level1Id || null;

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
      categoryId: selectedCategoryId(),
      customSpeciesNote: form.customSpeciesNote || null,
      gender: form.gender || "unknown",
      birthDate: form.birthDate || null,
      neutered: form.neutered,
      currentWeight: form.currentWeight === "" ? null : Number(form.currentWeight),
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
