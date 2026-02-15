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

    <FeedSection :feed-type="feedType" />
    <BackTop />
    <TabBar />
  </div>
</template>

<script setup>
import { onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import PetHeader from "../components/PetHeader.vue";
import FeedSection from "../components/FeedSection.vue";
import BackTop from "../components/BackTop.vue";
import TabBar from "../components/TabBar.vue";
import { api } from "../utils/api";
import { clearToken } from "../utils/auth";

const router = useRouter();
const currentPet = ref(null);
const pets = ref([]);
const feedType = ref("discover");

onMounted(async () => {
  await loadHeaderData();
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
  } catch {}
};

const goAddPet = () => {
  router.push("/pets/create");
};

const goPublish = () => {
  router.push("/pets/create");
};
</script>
