# =========================
# 构建阶段
# =========================
FROM maven:3.9.9-eclipse-temurin-21 AS builder

WORKDIR /build

# 先复制 pom.xml 以缓存依赖
COPY pom.xml ./
RUN mvn -q -B -DskipTests dependency:go-offline

# 再复制源码
COPY src ./src

# 打包
RUN mvn -q -B -DskipTests package

# 复制最终 jar（排除 original-）
RUN sh -c 'ls target/*.jar | grep -v "original-" | head -n 1 | xargs -I{} cp "{}" /build/app.jar'


# =========================
# 运行阶段
# =========================
FROM eclipse-temurin:21-jre

WORKDIR /app

# 创建运行目录 + 非 root 用户 + 健康检查依赖（curl）
RUN apt-get update \
  && apt-get install -y --no-install-recommends curl \
  && rm -rf /var/lib/apt/lists/* \
  && mkdir -p /app/uploads /app/AIlog \
  && addgroup --system appgroup \
  && adduser --system appuser --ingroup appgroup \
  && chown -R appuser:appgroup /app

# 复制 jar（直接设置属主，减少额外层）
COPY --chown=appuser:appgroup --from=builder /build/app.jar /app/app.jar

USER appuser

EXPOSE 8080

# JVM 内存限制（适合 2G 服务器）
ENV JAVA_OPTS="-Xms256m -Xmx512m"

# 可通过 docker-compose 或 -e 传入：
# SPRING_PROFILES_ACTIVE=prod
# DB_URL
# DB_USER
# DB_PASSWORD
# REDIS_HOST
# REDIS_PORT
# JWT_SECRET
# DASHSCOPE_API_KEY

# 健康检查（如果启用了 actuator）
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
