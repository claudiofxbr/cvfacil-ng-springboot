import { api } from './apiClient';

function mockFetchOnce({ status, body }) {
  global.fetch = jest.fn().mockResolvedValue({
    ok: status >= 200 && status < 300,
    status,
    statusText: 'OK',
    text: async () => body,
    json: async () => JSON.parse(body),
  });
}

describe('apiClient', () => {
  afterEach(() => {
    jest.restoreAllMocks();
  });

  // BUG ENCONTRADO ao investigar "troca de conta Google falha mesmo com o backend
  // funcionando": vários endpoints (relink/start, change-password, MFA, admin)
  // respondem 200 sem corpo (ResponseEntity.ok().build()), não 204 — chamar
  // res.json() incondicionalmente lança "Unexpected end of JSON input" nesse caso,
  // mesmo a requisicao tendo sido bem-sucedida.
  it('200 sem corpo (ex: relink/start) resolve com null, sem lançar', async () => {
    mockFetchOnce({ status: 200, body: '' });

    await expect(api.post('/api/auth/google/relink/start', {})).resolves.toBeNull();
  });

  it('204 continua resolvendo com null', async () => {
    mockFetchOnce({ status: 204, body: '' });

    await expect(api.delete('/api/resumes/1')).resolves.toBeNull();
  });

  it('200 com corpo JSON continua parseando normalmente', async () => {
    mockFetchOnce({ status: 200, body: '{"ok":true}' });

    await expect(api.get('/api/auth/security-status')).resolves.toEqual({ ok: true });
  });
});
