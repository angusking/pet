<template>
  <router-view />
  <TabBar v-if="showTabBar" />
</template>

<script setup>
import { computed } from "vue";
import { useRoute } from "vue-router";
import TabBar from "./components/TabBar.vue";

const route = useRoute();
const showTabBar = computed(() => Boolean(route.meta?.requiresAuth));
</script>
