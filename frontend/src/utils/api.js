import { getToken, clearToken } from "./auth";

const API_BASE = "";

async function request(path, options = {}) {
  const headers = { ...(options.headers || {}) };
  if (!(options.body instanceof FormData)) {
    headers["Content-Type"] = "application/json";
  }
  const token = getToken();
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }

  const response = await fetch(`${API_BASE}${path}`, {
    ...options,
    headers,
  });

  const text = await response.text();
  let data = null;
  if (text) {
    try {
      data = JSON.parse(text);
    } catch (e) {
      data = null;
    }
  }

  if (!response.ok) {
    if (response.status === 401) {
      clearToken();
    }
    const message = data?.message || text || "请求失败";
    const error = new Error(message);
    error.status = response.status;
    error.details = data?.data || data?.details;
    error.code = data?.code;
    throw error;
  }

  if (data && typeof data.code === "number" && data.code !== 0) {
    const error = new Error(data.message || "请求失败");
    error.details = data.data;
    error.code = data.code;
    throw error;
  }

  return data && typeof data.code === "number" ? data.data : data;
}

export const api = {
  get(path) {
    return request(path, { method: "GET" });
  },
  post(path, body) {
    return request(path, { method: "POST", body: JSON.stringify(body) });
  },
  delete(path) {
    return request(path, { method: "DELETE" });
  },
  upload(path, file) {
    const formData = new FormData();
    formData.append("file", file);
    return request(path, {
      method: "POST",
      body: formData,
      headers: {},
    });
  },
};
