<template>
  <div>
    <div id="top"></div>
    <PetHeader
      :current-pet="currentPet"
      :pets="pets"
      :feed-type="feedType"
      @change-feed="onChangeFeed"
      @select-pet="onSelectPet"
      @add-pet="goAddPet"
      @publish="goPublish"
    />

    <FeedSection
      :feed-type="feedType"
      :posts="posts"
      :loading="loadingPosts"
      :error="postError"
      @refresh="loadPosts"
    />
    <BackTop />
  </div>
</template>

<script setup>
import { onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import PetHeader from "../components/PetHeader.vue";
import FeedSection from "../components/FeedSection.vue";
import BackTop from "../components/BackTop.vue";
import { api } from "../utils/api";
import { clearToken } from "../utils/auth";

const router = useRouter();
const currentPet = ref(null);
const pets = ref([]);
const feedType = ref("discover");
const posts = ref([]);
const loadingPosts = ref(false);
const postError = ref("");
const AD_POSTS = [
  {
    id: "ad-food",
    ad: true,
    userId: "品牌推荐",
    city: "精选",
    title: "健康狗粮",
    content: "科学配比营养，呵护肠胃与皮毛，适合日常长期喂养。",
    tags: ["健康狗粮"],
    createdAt: "2026-02-27T08:00:00+08:00",
    mediaUrls: ["/assets/images/ad-food.jpg"],
  },
  {
    id: "ad-travel",
    ad: true,
    userId: "品牌推荐",
    city: "出行",
    title: "携宠旅行",
    content: "带上毛孩子一起出发，友好住宿与路线攻略一站搞定。",
    tags: ["携宠旅行"],
    createdAt: "2026-02-27T08:00:00+08:00",
    mediaUrls: ["/assets/images/ad-travel.jpg"],
  },
];

onMounted(async () => {
  await loadHeaderData();
  await loadPosts();
});

const loadHeaderData = async () => {
  try {
    const [primaryPet, petList] = await Promise.all([api.get("/api/me/pet"), api.get("/api/me/pets")]);
    currentPet.value = primaryPet;
    pets.value = Array.isArray(petList) ? petList : [];
  } catch (err) {
    if (err.status === 401) {
      clearToken();
      router.push("/login");
    }
  }
};

const onChangeFeed = (type) => {
  feedType.value = type;
  loadPosts();
};

const onSelectPet = async (pet) => {
  if (!pet?.id) return;
  try {
    if (pet.id !== currentPet.value?.id) {
      await api.post(`/api/pets/${pet.id}/primary`, {});
    }
    currentPet.value = pet;
    pets.value = pets.value.map((item) => ({ ...item, primary: item.id === pet.id }));
    router.push(`/pets/${pet.id}`);
    if (feedType.value === "follow") {
      loadPosts();
    }
  } catch {}
};

const goAddPet = () => {
  router.push("/pets/create");
};

const goPublish = () => {
  if (currentPet.value?.id) {
    router.push({ path: "/posts/create", query: { petId: String(currentPet.value.id) } });
    return;
  }
  router.push("/posts/create");
};

const loadPosts = async () => {
  loadingPosts.value = true;
  postError.value = "";
  try {
    const petId = feedType.value === "follow" ? currentPet.value?.id : null;
    const query = petId ? `/api/posts?petId=${petId}&page=0&size=20` : "/api/posts?page=0&size=20";
    const page = await api.get(query);
    const realPosts = Array.isArray(page?.content) ? page.content : [];
    posts.value = insertAdsByFixedPosition(realPosts, AD_POSTS);
  } catch (err) {
    postError.value = err.details?.[0] || err.message || "加载动态失败";
  } finally {
    loadingPosts.value = false;
  }
};

const insertAdsByFixedPosition = (list, ads) => {
  const result = [...list];
  if (!ads?.length) return result;

  const firstAd = { ...ads[0] };
  const secondAd = ads[1] ? { ...ads[1] } : null;

  // 默认放第4个位置（下标3）
  const firstIndex = Math.min(3, result.length);
  result.splice(firstIndex, 0, firstAd);

  // 第二个广告放最后一个
  if (secondAd) {
    result.push(secondAd);
  }
  return result;
};
</script>
