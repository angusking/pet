import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";
import sirv from "sirv";

export default defineConfig({
  plugins: [
    vue(),
    {
      name: "serve-external-files",
      configureServer(server) {
        const externalPath = "D:/pet/uploads";
        server.middlewares.use(
          "/uploads",
          sirv(externalPath, {
            dev: true,
            single: false,
            dotfiles: true,
          })
        );
      },
    },
  ],
  server: {
    fs: {
      allow: ["..", "D:/pet/uploads"],
    },
    proxy: {
      "/api": {
        target: "http://127.0.0.1:8080",
        changeOrigin: true,
      },
    },
  },
});
