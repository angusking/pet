FROM node:20-alpine AS builder

WORKDIR /app

RUN corepack enable

# Use domestic registry mirror during Docker build (pnpm / npm).
RUN pnpm config set registry https://registry.npmmirror.com \
  && npm config set registry https://registry.npmmirror.com

COPY package.json pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile

COPY . .
RUN pnpm build


FROM nginx:1.27-alpine

WORKDIR /usr/share/nginx/html

# Nginx official image renders /etc/nginx/templates/*.template via envsubst.
# Default upstream points to backend service in docker compose network.
ENV BACKEND_UPSTREAM=http://pet-backend:8080

COPY nginx.conf /etc/nginx/templates/default.conf.template
COPY --from=builder /app/dist ./

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
