const TOKEN_KEY = "pet_token";

export function setToken(token, expiresInSeconds) {
  const maxAge = expiresInSeconds ? `; max-age=${expiresInSeconds}` : "";
  document.cookie = `${TOKEN_KEY}=${encodeURIComponent(token)}; path=/; samesite=lax${maxAge}`;
}

export function getToken() {
  const match = document.cookie.match(new RegExp(`(?:^|; )${TOKEN_KEY}=([^;]*)`));
  return match ? decodeURIComponent(match[1]) : "";
}

export function clearToken() {
  document.cookie = `${TOKEN_KEY}=; path=/; max-age=0; samesite=lax`;
}
