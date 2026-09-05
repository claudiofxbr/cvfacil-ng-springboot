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
  return parseJsonBody(res);
}

/**
 * Vários endpoints (ex: /api/auth/google/relink/start, change-password, MFA, admin)
 * respondem 200 sem corpo (ResponseEntity.ok().build()), não 204 — chamar res.json()
 * incondicionalmente nesses casos lança "Unexpected end of JSON input" mesmo com a
 * requisição bem-sucedida, e o catch do componente mostra um erro genérico apesar do
 * backend ter funcionado. BUG ENCONTRADO ao investigar "troca de conta Google falha".
 */
async function parseJsonBody(res) {
  if (res.status === 204) return null;
  const text = await res.text();
  if (!text) return null;
  return JSON.parse(text);
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
  return parseJsonBody(res);
}

export const api = {
  get: (path) => apiFetch(path, { method: 'GET' }),
  post: (path, body) => apiFetch(path, { method: 'POST', body }),
  put: (path, body) => apiFetch(path, { method: 'PUT', body }),
  patch: (path, body) => apiFetch(path, { method: 'PATCH', body }),
  delete: (path, body) => apiFetch(path, { method: 'DELETE', body }),
};
