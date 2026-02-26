FROM maven:3.9.9-eclipse-temurin-21 AS builder

WORKDIR /build

# Copy Maven descriptor and Maven settings first for better cache reuse.
COPY pom.xml ./
COPY .mvn/settings.xml /root/.m2/settings.xml

# Pre-fetch dependencies using Aliyun mirror.
RUN mvn -s /root/.m2/settings.xml -B -DskipTests dependency:go-offline

# Copy source after dependency layer.
COPY src ./src
RUN mvn -s /root/.m2/settings.xml -B -DskipTests package

# Copy the runnable Spring Boot jar (exclude original-*.jar)
RUN sh -c 'ls target/*.jar | grep -v "original-" | head -n 1 | xargs -I{} cp "{}" /build/app.jar'


FROM eclipse-temurin:21-jre

WORKDIR /app

# Runtime dirs + non-root user + healthcheck dependency
RUN apt-get update \
  && apt-get install -y --no-install-recommends curl \
  && rm -rf /var/lib/apt/lists/* \
  && mkdir -p /app/uploads /app/AIlog \
  && addgroup --system appgroup \
  && adduser --system appuser --ingroup appgroup \
  && chown -R appuser:appgroup /app

COPY --chown=appuser:appgroup --from=builder /build/app.jar /app/app.jar

USER appuser

EXPOSE 8080

ENV JAVA_OPTS="\
-XX:InitialRAMPercentage=20 \
-XX:MaxRAMPercentage=40 \
-XX:+UseG1GC \
-XX:+ExitOnOutOfMemoryError"

HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
