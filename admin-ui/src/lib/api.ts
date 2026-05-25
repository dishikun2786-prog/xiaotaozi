const BASE_URL = "/api/v1/admin";

let accessToken: string | null = localStorage.getItem("admin_token");
let refreshToken: string | null = localStorage.getItem("admin_refresh");

export function setTokens(access: string, refresh: string): void {
  accessToken = access;
  refreshToken = refresh;
  localStorage.setItem("admin_token", access);
  localStorage.setItem("admin_refresh", refresh);
}

export function clearTokens(): void {
  accessToken = null;
  refreshToken = null;
  localStorage.removeItem("admin_token");
  localStorage.removeItem("admin_refresh");
}

export function getAccessToken(): string | null {
  return accessToken;
}

async function refreshAccessToken(): Promise<boolean> {
  if (!refreshToken) return false;
  try {
    const resp = await fetch(BASE_URL + "/auth/refresh", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ refreshToken }),
    });
    if (!resp.ok) { clearTokens(); return false; }
    const data = await resp.json();
    setTokens(data.accessToken, data.refreshToken);
    return true;
  } catch { clearTokens(); return false; }
}

export async function apiFetch(path: string, options: RequestInit = {}): Promise<Response> {
  const url = BASE_URL + path;
  const headers: Record<string, string> = {
    "Content-Type": "application/json",
    ...(options.headers as Record<string, string> || {}),
  };
  if (accessToken) headers["Authorization"] = "Bearer " + accessToken;

  let resp = await fetch(url, { ...options, headers });
  if (resp.status === 401 && refreshToken) {
    const refreshed = await refreshAccessToken();
    if (refreshed) {
      headers["Authorization"] = "Bearer " + accessToken;
      resp = await fetch(url, { ...options, headers });
    }
  }
  return resp;
}

export async function apiGet<T>(path: string): Promise<T> {
  const resp = await apiFetch(path);
  if (!resp.ok) throw new Error(await resp.text());
  return resp.json();
}

export async function apiPost<T>(path: string, body?: unknown): Promise<T> {
  const resp = await apiFetch(path, {
    method: "POST",
    body: body ? JSON.stringify(body) : undefined,
  });
  if (!resp.ok) throw new Error(await resp.text());
  return resp.json();
}

export async function apiPut<T>(path: string, body?: unknown): Promise<T> {
  const resp = await apiFetch(path, {
    method: "PUT",
    body: body ? JSON.stringify(body) : undefined,
  });
  if (!resp.ok) throw new Error(await resp.text());
  return resp.json();
}

export async function apiDelete(path: string): Promise<void> {
  const resp = await apiFetch(path, { method: "DELETE" });
  if (!resp.ok) throw new Error(await resp.text());
}
