FROM python:3.12-slim

WORKDIR /app

ENV PYTHONUNBUFFERED=1
ENV PIP_DISABLE_PIP_VERSION_CHECK=1
ENV PIP_DEFAULT_TIMEOUT=180

# 允许在不同环境下通过构建参数覆盖 pip 源。
# 默认使用国内镜像，降低 numpy / faiss / sentence-transformers 这类较大依赖下载超时的概率。
ARG PIP_INDEX_URL=https://pypi.tuna.tsinghua.edu.cn/simple
ARG PIP_TRUSTED_HOST=pypi.tuna.tsinghua.edu.cn

COPY requirements.txt .
RUN pip install --no-cache-dir --index-url ${PIP_INDEX_URL} --trusted-host ${PIP_TRUSTED_HOST} -r requirements.txt

COPY . .

# 运行时目录在镜像内预创建好，便于：
# 1. 容器首次启动时直接写日志和本地知识库目录；
# 2. 后续通过 volume 挂载宿主机目录时保持路径语义一致。
RUN mkdir -p /app/AIlog /app/data

EXPOSE 8001

HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
  CMD python -c "import urllib.request; urllib.request.urlopen('http://127.0.0.1:8001/health', timeout=3)"

CMD ["python", "run.py"]
