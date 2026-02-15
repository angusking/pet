<template>
  <div class="auth-page">
    <div class="auth-card">
      <h1>登录</h1>
      <p class="muted">欢迎回来，继续你的宠物社区</p>
      <form @submit.prevent="submit">
        <label>
          用户名
          <input v-model.trim="form.username" type="text" placeholder="请输入用户名" />
        </label>
        <label>
          密码
          <input v-model.trim="form.password" type="password" placeholder="请输入密码" />
        </label>
        <p v-if="error" class="error">{{ error }}</p>
        <button type="submit" class="primary full">登录</button>
      </form>
      <div class="auth-footer">
        <span>还没有账号？</span>
        <button type="button" class="link-btn" @click="goRegister">去注册</button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from "vue";
import { useRouter } from "vue-router";
import { api } from "../utils/api";
import { setToken } from "../utils/auth";

const router = useRouter();
const form = reactive({ username: "", password: "" });
const error = ref("");

const validate = () => {
  if (!form.username) return "请输入用户名";
  if (!form.password) return "请输入密码";
  return "";
};

const submit = async () => {
  error.value = validate();
  if (error.value) return;
  try {
    const data = await api.post("/api/auth/login", form);
    setToken(data.accessToken, data.expiresIn);
    router.push("/");
  } catch (err) {
    error.value = err.details?.[0] || err.message || "登录失败";
  }
};

const goRegister = () => {
  router.push("/register");
};
</script>
