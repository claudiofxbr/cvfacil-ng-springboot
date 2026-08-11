/**
 * Cliente HTTP para o backend CVFacil.NG.
 * - Injeta JWT armazenado em memória (useAuthStore) no header Authorization.
 * - Envia cookie httpOnly de refresh automaticamente (credentials: include).
 * - Adiciona X-XSRF-TOKEN quando disponível (double-submit CSRF).
 */
import { useAuthStore } from '@/lib/stores/authStore';

const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL || 'http://localhost:8080';

function readCsrfCookie() {
  if (typeof document === 'undefined') return null;
  const match = document.cookie.match(/(?:^|; )XSRF-TOKEN=([^;]+)/);
  return match ? decodeURIComponent(match[1]) : null;
}

export async function apiFetch(path, { method = 'GET', body, headers = {} } = {}) {
  const token = useAuthStore.getState?.().accessToken ?? null;
  const csrf = readCsrfCookie();

  const finalHeaders = {
    'Content-Type': 'application/json',
    Accept: 'application/json',
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
    ...(csrf && method !== 'GET' ? { 'X-XSRF-TOKEN': csrf } : {}),
    ...headers,
  };

  const res = await fetch(`${API_BASE_URL}${path}`, {
    method,
    headers: finalHeaders,
    credentials: 'include',
    body: body ? JSON.stringify(body) : undefined,
  });

  if (!res.ok) {
    const text = await res.text().catch(() => '');
    const error = new Error(`API ${res.status}: ${res.statusText}`);
    error.status = res.status;
    error.body = text;
    throw error;
  }
  if (res.status === 204) return null;
  return res.json();
}

/**
 * Versão de apiFetch para uploads multipart/form-data (FormData).
 * Não define Content-Type (o browser injeta o boundary correto automaticamente).
 * Adiciona X-XSRF-TOKEN e Authorization como apiFetch.
 * Retorna o objeto JSON parseado ou lança um Error com .status e .body.
 */
export async function apiFetchForm(path, formData) {
  const token = useAuthStore.getState?.().accessToken ?? null;
  const csrf = readCsrfCookie();

  const headers = {
    Accept: 'application/json',
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
    ...(csrf ? { 'X-XSRF-TOKEN': csrf } : {}),
  };

  const res = await fetch(`${API_BASE_URL}${path}`, {
    method: 'POST',
    headers,
    credentials: 'include',
    body: formData,
  });

  if (!res.ok) {
    const text = await res.text().catch(() => '');
    const error = new Error(`API ${res.status}: ${res.statusText}`);
    error.status = res.status;
    error.body = text;
    throw error;
  }
  if (res.status === 204) return null;
  return res.json();
}

export const api = {
  get: (path) => apiFetch(path, { method: 'GET' }),
  post: (path, body) => apiFetch(path, { method: 'POST', body }),
  put: (path, body) => apiFetch(path, { method: 'PUT', body }),
  patch: (path, body) => apiFetch(path, { method: 'PATCH', body }),
  delete: (path) => apiFetch(path, { method: 'DELETE' }),
};
