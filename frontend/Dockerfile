FROM node:20-alpine AS builder

WORKDIR /app

RUN corepack enable

COPY package.json pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile

COPY . .
RUN pnpm build


FROM nginx:1.27-alpine

WORKDIR /usr/share/nginx/html

# nginx 官方镜像会对 /etc/nginx/templates/*.template 做 envsubst
# 默认指向 docker-compose 常用服务名 backend
ENV BACKEND_UPSTREAM=http://backend:8080
ENV UPLOADS_UPSTREAM=http://backend:8080

COPY nginx.conf /etc/nginx/templates/default.conf.template
COPY --from=builder /app/dist ./

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
