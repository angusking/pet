<template>
  <header ref="headerRef" class="home-header">
    <div class="header-top">
      <button class="pet-switch" type="button" @click="toggleDrawer">
        <img class="pet-switch-avatar" :src="currentAvatar" alt="宠物头像" />
        <span class="pet-switch-arrow" :class="{ open: drawerOpen }">▼</span>
      </button>

      <div class="feed-tabs" role="tablist" aria-label="信息流切换">
        <button
          type="button"
          class="feed-tab"
          :class="{ active: feedType === 'follow' }"
          @click="changeFeed('follow')"
        >
          关注
        </button>
        <button
          type="button"
          class="feed-tab"
          :class="{ active: feedType === 'discover' }"
          @click="changeFeed('discover')"
        >
          发现
        </button>
      </div>

      <button class="publish-btn" type="button" @click="$emit('publish')">发布</button>
    </div>

    <transition name="pet-popover-transition">
      <section v-if="drawerOpen" class="pet-popover">
        <div class="drawer-head">
          <h3>切换宠物</h3>
          <button type="button" class="close-btn" @click="closeDrawer">关闭</button>
        </div>

        <div v-if="pets.length" class="pet-list">
          <button
            v-for="item in pets"
            :key="item.id"
            type="button"
            class="pet-item"
            :class="{ active: item.id === currentPetId }"
            @click="onSelectPet(item)"
          >
            <img class="pet-item-avatar" :src="item.avatarUrl || '/assets/images/avatar.jpg'" alt="宠物头像" />
            <div class="pet-item-main">
              <div class="pet-item-name">{{ item.name || "未命名" }}</div>
              <div class="pet-item-sub">{{ petLine(item) }}</div>
              <div class="pet-item-tags">
                <span class="state-tag">{{ item.tags?.[0] || "健康正常" }}</span>
                <span v-if="item.id === currentPetId" class="current-tag">当前宠物</span>
              </div>
            </div>
          </button>
        </div>
        <div v-else class="empty-pets">
          <p>你还没有添加宠物，先创建一只宠物档案。</p>
        </div>

        <button class="add-pet-btn" type="button" @click="$emit('add-pet')">添加宠物</button>
      </section>
    </transition>
  </header>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from "vue";

const props = defineProps({
  currentPet: {
    type: Object,
    default: null,
  },
  pets: {
    type: Array,
    default: () => [],
  },
  feedType: {
    type: String,
    default: "discover",
  },
});

const emit = defineEmits(["change-feed", "select-pet", "add-pet", "publish"]);
const drawerOpen = ref(false);
const headerRef = ref(null);

const currentAvatar = computed(() => props.currentPet?.avatarUrl || "/assets/images/avatar.jpg");
const currentPetId = computed(() => props.currentPet?.id ?? null);

const petLine = (pet) => {
  const breed = pet.breed || "未知品种";
  const weight = pet.weightKg != null ? `${pet.weightKg}kg` : "--kg";
  return `${breed} · ${weight}`;
};

const toggleDrawer = () => {
  drawerOpen.value = !drawerOpen.value;
};

const closeDrawer = () => {
  drawerOpen.value = false;
};

const changeFeed = (type) => {
  emit("change-feed", type);
};

const onSelectPet = (pet) => {
  emit("select-pet", pet);
  closeDrawer();
};

const onWindowClick = (event) => {
  if (!drawerOpen.value) return;
  if (!headerRef.value) return;
  if (!headerRef.value.contains(event.target)) {
    closeDrawer();
  }
};

onMounted(() => {
  window.addEventListener("click", onWindowClick);
});

onBeforeUnmount(() => {
  window.removeEventListener("click", onWindowClick);
});
</script>
