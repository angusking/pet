<template>
  <div class="auth-page">
    <div class="auth-card">
      <h1>注册</h1>
      <p class="muted">创建账号，开始记录宠物生活</p>
      <form @submit.prevent="submit">
        <label>
          用户名
          <input v-model.trim="form.username" type="text" placeholder="请输入用户名" />
        </label>
        <label>
          密码
          <input v-model.trim="form.password" type="password" placeholder="请输入密码" />
        </label>
        <label>
          昵称（可选）
          <input v-model.trim="form.nickname" type="text" placeholder="请输入昵称" />
        </label>
        <p v-if="error" class="error">{{ error }}</p>
        <button type="submit" class="primary full">注册并登录</button>
      </form>
      <div class="auth-footer">
        <span>已有账号？</span>
        <button type="button" class="link-btn" @click="goLogin">去登录</button>
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
const form = reactive({ username: "", password: "", nickname: "" });
const error = ref("");

const validate = () => {
  if (!form.username) return "请输入用户名";
  if (!form.password) return "请输入密码";
  if (form.password.length < 6) return "密码至少 6 位";
  if (!/[A-Za-z]/.test(form.password)) return "密码需包含字母";
  if (!/[0-9]/.test(form.password)) return "密码需包含数字";
  return "";
};

const submit = async () => {
  error.value = validate();
  if (error.value) return;
  try {
    const data = await api.post("/api/auth/register", form);
    setToken(data.accessToken, data.expiresIn);
    router.push("/");
  } catch (err) {
    error.value = err.details?.[0] || err.message || "注册失败";
  }
};

const goLogin = () => {
  router.push("/login");
};
</script>
