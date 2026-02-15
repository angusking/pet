import { createRouter, createWebHistory } from "vue-router";
import LoginView from "./views/LoginView.vue";
import RegisterView from "./views/RegisterView.vue";
import HomeView from "./views/HomeView.vue";
import PetCreateView from "./views/PetCreateView.vue";
import PetDetailView from "./views/PetDetailView.vue";
import { getToken } from "./utils/auth";

const routes = [
  { path: "/login", name: "login", component: LoginView },
  { path: "/register", name: "register", component: RegisterView },
  { path: "/", name: "home", component: HomeView, meta: { requiresAuth: true } },
  { path: "/pets/create", name: "pet-create", component: PetCreateView, meta: { requiresAuth: true } },
  { path: "/pets/:id", name: "pet-detail", component: PetDetailView, meta: { requiresAuth: true } },
];

const router = createRouter({
  history: createWebHistory(),
  routes,
});

router.beforeEach((to) => {
  const token = getToken();
  if (to.meta.requiresAuth && !token) {
    return { name: "login" };
  }
  if ((to.name === "login" || to.name === "register") && token) {
    return { name: "home" };
  }
  return true;
});

export default router;
