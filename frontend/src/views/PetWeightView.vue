<template>
  <div class="pet-weight-page">
    <header class="pet-weight-top">
      <button type="button" class="back-btn" @click="goBack">返回</button>
      <div class="pet-weight-title">体重记录</div>
      <div class="pet-weight-top-space"></div>
    </header>

    <main class="pet-weight-main">
      <section class="pet-weight-hero card-lite">
        <p v-if="loadingPet" class="muted">正在加载宠物资料...</p>
        <p v-else-if="petError" class="error">{{ petError }}</p>
        <template v-else-if="pet">
          <div class="pet-weight-pet-row">
            <img class="pet-weight-avatar" :src="pet.avatarUrl || '/assets/images/avatar.jpg'" :alt="pet.name || '宠物头像'" />
            <div class="pet-weight-pet-main">
              <div class="pet-weight-name">{{ pet.name || "未命名宠物" }}</div>
              <div class="pet-weight-meta">
                {{ pet.breed || pet.categoryName || "未知分类" }}
                <span v-if="pet.customSpeciesNote">· {{ pet.customSpeciesNote }}</span>
              </div>
              <div class="pet-weight-sub">当前体重 {{ summary.currentWeight != null ? `${summary.currentWeight} kg` : "--" }}</div>
            </div>
          </div>
          <p class="pet-weight-intro">
            记录宠物体重变化，帮助观察日常喂养与状态趋势。不同宠物类别体重差异很大，下面的参考说明仅用于辅助理解，不替代专业诊疗建议。
          </p>
        </template>
      </section>

      <section class="pet-weight-summary card-lite">
        <div class="summary-head">
          <div>
            <h3>趋势概览</h3>
            <p class="muted">第一行看前一次和当前值，第二行看最近十次折线趋势</p>
          </div>
          <button type="button" class="ghost-btn" @click="reloadAll" :disabled="loadingSummary">
            {{ loadingSummary ? "刷新中..." : "刷新" }}
          </button>
        </div>

        <p v-if="summaryError" class="error">{{ summaryError }}</p>

        <div class="summary-inline-row">
          <article class="summary-inline-item">
            <span class="label">前一次</span>
            <strong>{{ summary.previousWeight != null ? `${summary.previousWeight} kg` : "--" }}</strong>
          </article>
          <article class="summary-inline-item current">
            <span class="label">当前</span>
            <strong>{{ summary.currentWeight != null ? `${summary.currentWeight} kg` : "--" }}</strong>
            <span class="sub">{{ formatDateTime(summary.latestRecordedAt) || "暂无记录" }}</span>
          </article>
          <button type="button" class="summary-inline-action" @click="openWeightModal">添加</button>
        </div>

        <div class="summary-chart-card">
          <div class="summary-chart-head">
            <span>最近 10 次记录</span>
            <span class="hint-badge">{{ supportLevelText }}</span>
          </div>
          <div v-if="chartPoints.length >= 2" class="summary-chart-shell">
            <svg viewBox="0 0 320 140" preserveAspectRatio="none" aria-label="最近十次体重折线图">
              <polyline class="chart-line" :points="chartPolyline" />
              <g v-for="point in chartPoints" :key="point.key">
                <text class="chart-value-label" :x="point.x" :y="point.labelY" text-anchor="middle">
                  {{ point.label }}
                </text>
                <circle class="chart-dot" :cx="point.x" :cy="point.y" r="3.5" />
              </g>
            </svg>
            <div class="summary-chart-axis">
              <span>{{ chartStartLabel }}</span>
              <span>{{ chartEndLabel }}</span>
            </div>
          </div>
          <p v-else class="muted">至少需要两条体重记录才能展示折线图。</p>
          <p class="summary-chart-hint">
            {{ summary.categoryWeightHint || "该宠物类别个体差异较大，建议优先观察连续趋势。" }}
          </p>
        </div>
      </section>

      <section class="pet-weight-list card-lite">
        <div class="section-head">
          <div>
            <h3>历史记录</h3>
            <p class="muted">按时间倒序展示，便于快速查看最新趋势</p>
          </div>
        </div>

        <p v-if="loadingSummary && !records.length" class="muted">正在加载体重记录...</p>
        <div v-else-if="records.length" class="weight-records">
          <article v-for="record in records" :key="record.id" class="weight-record-item">
            <div class="weight-record-main">
              <div class="weight-record-value">
                <strong>{{ record.weightValue }} kg</strong>
                <span :class="['delta', resolveDeltaClass(record.changeFromPrevious)]">
                  {{ formatWeightChange(record.changeFromPrevious) }}
                </span>
              </div>
              <div class="weight-record-meta">
                <span>{{ formatDateTime(record.recordedAt) }}</span>
                <span>{{ sourceText(record.source) }}</span>
              </div>
              <p v-if="record.note" class="weight-record-note">{{ record.note }}</p>
            </div>
            <button
              type="button"
              class="ghost-btn danger-outline"
              :disabled="deletingRecordId === record.id"
              @click="deleteRecord(record)"
            >
              {{ deletingRecordId === record.id ? "删除中..." : "删除" }}
            </button>
          </article>
        </div>
        <div v-else class="pet-weight-empty">
          <p>还没有体重记录，先添加第一条。</p>
        </div>
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

        <form class="weight-modal-form" @submit.prevent="submitRecord">
          <label>
            <span>记录时间</span>
            <input v-model="form.recordedAt" type="datetime-local" required />
          </label>
          <label>
            <span>体重（kg）</span>
            <input v-model="form.weightValue" type="number" step="0.01" min="0.01" placeholder="例如 4.25" required />
          </label>
          <label>
            <span>记录来源</span>
            <select v-model="form.source">
              <option value="home">家用称重</option>
              <option value="clinic">医院称重</option>
              <option value="other">其他</option>
            </select>
          </label>
          <label class="full">
            <span>备注</span>
            <textarea v-model="form.note" rows="3" maxlength="255" placeholder="例如：饭前、洗澡后、刚换粮一周"></textarea>
          </label>
          <p v-if="submitError" class="error full">{{ submitError }}</p>
          <button type="submit" class="primary full" :disabled="submitting">
            {{ submitting ? "保存中..." : "保存体重记录" }}
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
const petId = computed(() => route.params.id);

const pet = ref(null);
const records = ref([]);
const summary = reactive({
  currentWeight: null,
  latestRecordedAt: "",
  previousWeight: null,
  changeFromPrevious: null,
  categorySupportLevel: "trend_only",
  categoryWeightHint: "",
});

const loadingPet = ref(true);
const loadingSummary = ref(true);
const petError = ref("");
const summaryError = ref("");
const submitError = ref("");
const submitting = ref(false);
const deletingRecordId = ref(null);
const weightModalOpen = ref(false);

const form = reactive({
  weightValue: "",
  source: "home",
  note: "",
  recordedAt: toDatetimeLocalValue(new Date()),
});

const supportLevelText = computed(() => {
  return summary.categorySupportLevel === "precise" ? "可结合分类参考" : "以趋势观察为主";
});

const recentChartRecords = computed(() => {
  return [...records.value].slice(0, 10).reverse();
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
  await reloadAll();
});

const reloadAll = async () => {
  await Promise.all([loadPet(), loadWeights()]);
};

const loadPet = async () => {
  loadingPet.value = true;
  petError.value = "";
  try {
    pet.value = await api.get(`/api/pets/${petId.value}`);
  } catch (err) {
    petError.value = err.details?.[0] || err.message || "加载宠物资料失败";
  } finally {
    loadingPet.value = false;
  }
};

const loadWeights = async () => {
  loadingSummary.value = true;
  summaryError.value = "";
  try {
    const payload = await petWeightApi.list(petId.value);
    records.value = Array.isArray(payload?.records) ? payload.records : [];
    Object.assign(summary, {
      currentWeight: payload?.summary?.currentWeight ?? null,
      latestRecordedAt: payload?.summary?.latestRecordedAt ?? "",
      previousWeight: payload?.summary?.previousWeight ?? null,
      changeFromPrevious: payload?.summary?.changeFromPrevious ?? null,
      categorySupportLevel: payload?.summary?.categorySupportLevel ?? "trend_only",
      categoryWeightHint: payload?.summary?.categoryWeightHint ?? "",
    });
  } catch (err) {
    records.value = [];
    Object.assign(summary, {
      currentWeight: null,
      latestRecordedAt: "",
      previousWeight: null,
      changeFromPrevious: null,
      categorySupportLevel: "trend_only",
      categoryWeightHint: "",
    });
    summaryError.value = err.details?.[0] || err.message || "加载体重记录失败";
  } finally {
    loadingSummary.value = false;
  }
};

const openWeightModal = () => {
  submitError.value = "";
  form.recordedAt = toDatetimeLocalValue(new Date());
  weightModalOpen.value = true;
};

const closeWeightModal = () => {
  if (submitting.value) return;
  weightModalOpen.value = false;
};

const submitRecord = async () => {
  if (submitting.value) return;
  submitting.value = true;
  submitError.value = "";
  try {
    await petWeightApi.create(petId.value, {
      weightValue: Number(form.weightValue),
      unit: "kg",
      source: form.source,
      note: form.note?.trim() || null,
      recordedAt: new Date(form.recordedAt).toISOString().slice(0, 19),
    });
    form.weightValue = "";
    form.note = "";
    form.recordedAt = toDatetimeLocalValue(new Date());
    await Promise.all([loadPet(), loadWeights()]);
    weightModalOpen.value = false;
  } catch (err) {
    submitError.value = err.details?.[0] || err.message || "保存体重记录失败";
  } finally {
    submitting.value = false;
  }
};

const deleteRecord = async (record) => {
  if (!record?.id || deletingRecordId.value) return;
  const confirmed = window.confirm(`确认删除 ${record.weightValue} kg 这条体重记录吗？`);
  if (!confirmed) return;

  deletingRecordId.value = record.id;
  summaryError.value = "";
  try {
    await petWeightApi.remove(petId.value, record.id);
    await Promise.all([loadPet(), loadWeights()]);
  } catch (err) {
    summaryError.value = err.details?.[0] || err.message || "删除体重记录失败";
  } finally {
    deletingRecordId.value = null;
  }
};

const goBack = () => {
  router.push(`/pets/${petId.value}`);
};

const formatDateTime = (value) => {
  if (!value) return "";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleString();
};

const formatShortDate = (value) => {
  if (!value) return "";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${month}-${day}`;
};

const formatWeightChange = (value) => {
  if (value == null || value === "") return "--";
  const numberValue = Number(value);
  if (Number.isNaN(numberValue)) return value;
  if (numberValue > 0) return `+${numberValue.toFixed(2)} kg`;
  if (numberValue < 0) return `${numberValue.toFixed(2)} kg`;
  return "0.00 kg";
};

const resolveDeltaClass = (value) => {
  const numberValue = Number(value);
  if (Number.isNaN(numberValue) || value == null) return "neutral";
  if (numberValue > 0) return "up";
  if (numberValue < 0) return "down";
  return "neutral";
};

const sourceText = (value) => {
  const map = {
    home: "家用称重",
    clinic: "医院称重",
    other: "其他来源",
  };
  return map[value] || "未标注来源";
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
