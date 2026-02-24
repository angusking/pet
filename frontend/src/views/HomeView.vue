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
    posts.value = page?.content || [];
  } catch (err) {
    postError.value = err.details?.[0] || err.message || "加载动态失败";
  } finally {
    loadingPosts.value = false;
  }
};
</script>
