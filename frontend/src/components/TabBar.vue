<template>
  <nav class="tabbar">
    <router-link class="tab" :class="{ active: route.path === '/' }" to="/">首页</router-link>
    <a class="tab" href="#">市集</a>
    <router-link class="tab" :class="{ active: route.path === '/posts/create' }" to="/posts/create">发布</router-link>
    <a class="tab has-dot" href="#">助手</a>
    <router-link class="tab" :class="{ active: route.path === '/me' }" to="/me">我的</router-link>
  </nav>
</template>

<script setup>
import { useRoute } from "vue-router";
const route = useRoute();
</script>
