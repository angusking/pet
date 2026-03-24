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
          <div class="pet-screen-name">{{ pet.name || "未命名宠物" }}</div>
          <div class="pet-screen-meta">{{ pet.breed || pet.categoryName || "未知品种" }} · {{ petGender }}</div>
          <div class="pet-screen-tags">
            <span class="tag">体重 {{ pet.currentWeight != null ? `${pet.currentWeight}kg` : "--" }}</span>
            <span class="tag">生日 {{ pet.birthDate || "未知" }}</span>
            <span class="tag">绝育 {{ pet.neutered == null ? "未知" : pet.neutered ? "已绝育" : "未绝育" }}</span>
          </div>
        </template>
      </section>

      <section v-if="pet" class="pet-weight-card">
        <div class="pet-weight-card-head">
          <div>
            <h3>体重摘要</h3>
            <p class="muted">第一行看前一次和当前值，第二行看最近十次趋势</p>
          </div>
          <button type="button" class="ghost-btn" @click="goWeightPage">查看全部</button>
        </div>

        <p v-if="weightError" class="error">{{ weightError }}</p>
        <p v-else-if="loadingWeightSummary" class="muted">正在加载体重摘要...</p>
        <template v-else>
          <div class="pet-weight-inline-row">
            <article class="pet-weight-inline-item">
              <span class="label">前一次</span>
              <strong>{{ weightSummary.previousWeight != null ? `${weightSummary.previousWeight} kg` : "--" }}</strong>
            </article>
            <article class="pet-weight-inline-item current">
              <span class="label">当前</span>
              <strong>{{ weightSummary.currentWeight != null ? `${weightSummary.currentWeight} kg` : "--" }}</strong>
              <span class="sub">{{ formatTime(weightSummary.latestRecordedAt) || "暂无记录" }}</span>
            </article>
            <button type="button" class="pet-weight-inline-action" @click="openWeightModal">添加</button>
          </div>

          <div class="pet-weight-chart-card">
            <div class="pet-weight-chart-head">
              <span>最近 10 次记录</span>
              <span class="support-badge">{{ supportLevelText }}</span>
            </div>
            <div v-if="chartPoints.length >= 2" class="pet-weight-chart">
              <svg viewBox="0 0 320 140" preserveAspectRatio="none" aria-label="最近十次体重折线图">
                <polyline class="chart-line" :points="chartPolyline" />
                <g v-for="point in chartPoints" :key="point.key">
                  <text class="chart-value-label" :x="point.x" :y="point.labelY" text-anchor="middle">
                    {{ point.label }}
                  </text>
                  <circle class="chart-dot" :cx="point.x" :cy="point.y" r="3.5" />
                </g>
              </svg>
              <div class="pet-weight-chart-axis">
                <span>{{ chartStartLabel }}</span>
                <span>{{ chartEndLabel }}</span>
              </div>
            </div>
            <p v-else class="muted">至少需要两条体重记录才能展示折线图。</p>
            <p class="pet-weight-chart-hint">
              {{ weightSummary.categoryWeightHint || "该宠物类别建议优先观察连续趋势。" }}
            </p>
          </div>
        </template>
      </section>

      <section class="pet-screen-posts">
        <div class="pet-screen-posts-head">
          <h3>宠物相关日记</h3>
          <button type="button" class="plus-btn" aria-label="发布" @click="goPublish">+</button>
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
          <p>还没有这只宠物的相关日记，去添加第一篇。</p>
        </div>
      </section>

      <section v-if="pet" class="pet-screen-danger">
        <div class="danger-head">
          <strong>危险操作</strong>
          <span>删除后无法恢复</span>
        </div>
        <p class="muted">删除宠物档案后，当前宠物资料和关联体重记录都会被移除。</p>
        <p v-if="deleteError" class="error">{{ deleteError }}</p>
        <button type="button" class="danger-btn" :disabled="deletingPet" @click="deletePet">
          {{ deletingPet ? "删除中..." : "删除宠物档案" }}
        </button>
      </section>
    </main>

    <Teleport to="body">
      <div v-if="weightModalOpen" class="weight-modal-overlay" @click="closeWeightModal"></div>
      <section v-if="weightModalOpen" class="weight-modal" aria-label="添加体重记录">
        <div class="weight-modal-head">
          <div>
            <strong>添加体重记录</strong>
            <p class="muted">保存后会同步刷新摘要和折线图</p>
          </div>
          <button type="button" class="ghost-btn" @click="closeWeightModal">关闭</button>
        </div>

        <form class="weight-modal-form" @submit.prevent="submitWeightRecord">
          <label>
            <span>记录时间</span>
            <input v-model="weightForm.recordedAt" type="datetime-local" required />
          </label>
          <label>
            <span>体重（kg）</span>
            <input v-model="weightForm.weightValue" type="number" step="0.01" min="0.01" placeholder="例如 4.25" required />
          </label>
          <label>
            <span>记录来源</span>
            <select v-model="weightForm.source">
              <option value="home">家用称重</option>
              <option value="clinic">医院称重</option>
              <option value="other">其他</option>
            </select>
          </label>
          <label class="full">
            <span>备注</span>
            <textarea v-model="weightForm.note" rows="3" maxlength="255" placeholder="例如：饭前、洗澡后、刚换粮一周"></textarea>
          </label>
          <p v-if="weightSubmitError" class="error full">{{ weightSubmitError }}</p>
          <button type="submit" class="primary full" :disabled="submittingWeight">
            {{ submittingWeight ? "保存中..." : "保存体重记录" }}
          </button>
        </form>
      </section>
    </Teleport>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { api } from "../utils/api";
import { petWeightApi } from "../utils/petWeightApi";

const route = useRoute();
const router = useRouter();
const pet = ref(null);
const posts = ref([]);
const loadingPet = ref(true);
const loadingPosts = ref(true);
const loadingWeightSummary = ref(true);
const petError = ref("");
const postsError = ref("");
const weightError = ref("");
const deletingPet = ref(false);
const deleteError = ref("");
const weightRecords = ref([]);
const weightModalOpen = ref(false);
const submittingWeight = ref(false);
const weightSubmitError = ref("");

const weightSummary = reactive({
  currentWeight: null,
  latestRecordedAt: "",
  previousWeight: null,
  changeFromPrevious: null,
  categorySupportLevel: "trend_only",
  categoryWeightHint: "",
});

const weightForm = reactive({
  weightValue: "",
  source: "home",
  note: "",
  recordedAt: toDatetimeLocalValue(new Date()),
});

const petGender = computed(() => {
  if (!pet.value?.gender || pet.value.gender === "unknown") return "未知";
  return pet.value.gender === "male" ? "公" : "母";
});

const supportLevelText = computed(() => {
  return weightSummary.categorySupportLevel === "precise" ? "可结合分类参考" : "以趋势观察为主";
});

const recentChartRecords = computed(() => {
  return [...weightRecords.value].slice(0, 10).reverse();
});

const chartPoints = computed(() => {
  const items = recentChartRecords.value;
  if (items.length < 2) return [];
  const values = items.map((item) => Number(item.weightValue)).filter((value) => !Number.isNaN(value));
  if (values.length < 2) return [];

  const min = Math.min(...values);
  const max = Math.max(...values);
  const range = Math.max(max - min, 0.2);
  const width = 320;
  const height = 140;
  const left = 12;
  const right = 12;
  const top = 28;
  const bottom = 20;
  const usableWidth = width - left - right;
  const usableHeight = height - top - bottom;

  return items.map((item, index) => {
    const value = Number(item.weightValue);
    const x = left + (usableWidth * index) / Math.max(items.length - 1, 1);
    const normalized = (value - min) / range;
    const y = height - bottom - normalized * usableHeight;
    const labelY = Math.max(y - 8, 14);
    return {
      key: item.id,
      x: Number(x.toFixed(2)),
      y: Number(y.toFixed(2)),
      labelY: Number(labelY.toFixed(2)),
      label: `${Number(value.toFixed(2))}`,
    };
  });
});

const chartPolyline = computed(() => chartPoints.value.map((point) => `${point.x},${point.y}`).join(" "));

const chartStartLabel = computed(() => {
  return recentChartRecords.value[0] ? formatShortDate(recentChartRecords.value[0].recordedAt) : "";
});

const chartEndLabel = computed(() => {
  const last = recentChartRecords.value[recentChartRecords.value.length - 1];
  return last ? formatShortDate(last.recordedAt) : "";
});

onMounted(async () => {
  const id = route.params.id;
  await Promise.all([loadPet(id), loadPosts(id), loadWeightSummary(id)]);
});

const loadPet = async (id) => {
  try {
    pet.value = await api.get(`/api/pets/${id}`);
  } catch (err) {
    petError.value = err.details?.[0] || err.message || "加载失败";
  } finally {
    loadingPet.value = false;
  }
};

const loadPosts = async (id) => {
  try {
    const page = await api.get(`/api/posts?petId=${id}&page=0&size=20`);
    posts.value = page?.content || [];
  } catch (err) {
    postsError.value = err.details?.[0] || err.message || "加载日记失败";
  } finally {
    loadingPosts.value = false;
  }
};

const loadWeightSummary = async (id) => {
  try {
    const payload = await petWeightApi.list(id);
    weightRecords.value = Array.isArray(payload?.records) ? payload.records : [];
    Object.assign(weightSummary, {
      currentWeight: payload?.summary?.currentWeight ?? null,
      latestRecordedAt: payload?.summary?.latestRecordedAt ?? "",
      previousWeight: payload?.summary?.previousWeight ?? null,
      changeFromPrevious: payload?.summary?.changeFromPrevious ?? null,
      categorySupportLevel: payload?.summary?.categorySupportLevel ?? "trend_only",
      categoryWeightHint: payload?.summary?.categoryWeightHint ?? "",
    });
  } catch (err) {
    weightError.value = err.details?.[0] || err.message || "加载体重摘要失败";
  } finally {
    loadingWeightSummary.value = false;
  }
};

const openWeightModal = () => {
  weightSubmitError.value = "";
  weightForm.recordedAt = toDatetimeLocalValue(new Date());
  weightModalOpen.value = true;
};

const closeWeightModal = () => {
  if (submittingWeight.value) return;
  weightModalOpen.value = false;
};

const submitWeightRecord = async () => {
  if (submittingWeight.value || !pet.value?.id) return;
  submittingWeight.value = true;
  weightSubmitError.value = "";
  try {
    await petWeightApi.create(pet.value.id, {
      weightValue: Number(weightForm.weightValue),
      unit: "kg",
      source: weightForm.source,
      note: weightForm.note?.trim() || null,
      recordedAt: new Date(weightForm.recordedAt).toISOString().slice(0, 19),
    });
    weightForm.weightValue = "";
    weightForm.note = "";
    weightForm.recordedAt = toDatetimeLocalValue(new Date());
    await Promise.all([loadPet(pet.value.id), loadWeightSummary(pet.value.id)]);
    weightModalOpen.value = false;
  } catch (err) {
    weightSubmitError.value = err.details?.[0] || err.message || "保存体重记录失败";
  } finally {
    submittingWeight.value = false;
  }
};

const formatTime = (value) => {
  if (!value) return "";
  const d = new Date(value);
  if (Number.isNaN(d.getTime())) return value;
  return d.toLocaleString();
};

const formatShortDate = (value) => {
  if (!value) return "";
  const d = new Date(value);
  if (Number.isNaN(d.getTime())) return value;
  const month = String(d.getMonth() + 1).padStart(2, "0");
  const day = String(d.getDate()).padStart(2, "0");
  return `${month}-${day}`;
};

const deletePet = async () => {
  if (!pet.value?.id || deletingPet.value) return;
  const confirmed = window.confirm(`确认删除宠物“${pet.value.name || "未命名宠物"}”吗？删除后无法恢复。`);
  if (!confirmed) return;

  deletingPet.value = true;
  deleteError.value = "";
  try {
    await api.delete(`/api/pets/${pet.value.id}`);
    router.push("/");
  } catch (err) {
    deleteError.value = err.details?.[0] || err.message || "删除宠物失败";
  } finally {
    deletingPet.value = false;
  }
};

const goHome = () => {
  router.push("/");
};

const goPostDetail = (id) => {
  if (!id) return;
  router.push(`/posts/${id}`);
};

const goPublish = () => {
  if (!pet.value?.id) return;
  router.push({ path: "/posts/create", query: { petId: String(pet.value.id) } });
};

const goWeightPage = () => {
  if (!pet.value?.id) return;
  router.push(`/pets/${pet.value.id}/weights`);
};

function toDatetimeLocalValue(date) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  const hours = String(date.getHours()).padStart(2, "0");
  const minutes = String(date.getMinutes()).padStart(2, "0");
  return `${year}-${month}-${day}T${hours}:${minutes}`;
}
</script>
