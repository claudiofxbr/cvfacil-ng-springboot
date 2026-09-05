'use client';

import Link from 'next/link';
import { useState, useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { z } from 'zod';
import { Header } from '@/components/ui/Header';
import { FadeIn } from '@/components/ui/Motion';
import { api } from '@/lib/apiClient';
import { useAuthStore } from '@/lib/stores/authStore';

const loginSchema = z.object({
  email: z.string().email(),
  password: z.string().min(10, 'Mínimo 10 caracteres'),
});

export default function LoginPage() {
  const router = useRouter();
  const setSession = useAuthStore((s) => s.setSession);
  const [form, setForm] = useState({ email: '', password: '' });
  const [error, setError] = useState('');
  const [info, setInfo] = useState('');
  const [loading, setLoading] = useState(false);
  // Mock OAuth: true quando o backend redireciona com ?mock_oauth=true (credenciais dummy em dev)
  const [mockOauth, setMockOauth] = useState(false);
  const [mockEmail, setMockEmail] = useState('');
  const [mockLoading, setMockLoading] = useState(false);
  // Segundo fator (MFA): quando login() responde 202 mfaRequired, guardamos o
  // challengeToken de 5min e pedimos o código de 6 dígitos do app autenticador.
  const [mfaChallenge, setMfaChallenge] = useState('');
  const [mfaCode, setMfaCode] = useState('');
  const [mfaLoading, setMfaLoading] = useState(false);

  const apiBase = process.env.NEXT_PUBLIC_API_BASE_URL || 'http://localhost:8080';

  // Lê parâmetros de URL após a montagem para evitar erros de hidratação SSR
  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    const err = params.get('error');
    const registered = params.get('registered');
    const mock = params.get('mock_oauth');
    if (mock === 'true') {
      setMockOauth(true);
    } else if (err === 'oauth_failed') {
      setError('Login com Google falhou. Verifique se as credenciais OAuth estão corretas e se o redirect URI está cadastrado no Google Cloud Console.');
    } else if (err) {
      setError('Ocorreu um erro durante a autenticação. Tente novamente.');
    }
    if (registered === 'true') {
      setInfo('Conta criada com sucesso! Faça login para continuar.');
    }
  }, []);

  async function onMockGoogle(e) {
    e.preventDefault();
    if (!mockEmail.trim()) return;
    setMockLoading(true);
    setError('');
    try {
      const resp = await api.post('/api/auth/mock-google', { email: mockEmail.trim() });
      setSession(resp.user, resp.accessToken);
      router.push('/dashboard');
    } catch {
      setError('Não foi possível autenticar. Tente novamente.');
    } finally {
      setMockLoading(false);
    }
  }

  async function onSubmit(e) {
    e.preventDefault();
    setError('');
    const parsed = loginSchema.safeParse(form);
    if (!parsed.success) {
      setError(parsed.error.errors[0]?.message || 'Entrada inválida');
      return;
    }
    setLoading(true);
    try {
      const resp = await api.post('/api/auth/login', parsed.data);
      if (resp.mfaRequired) {
        setMfaChallenge(resp.challengeToken);
        return;
      }
      setSession(resp.user, resp.accessToken);
      // router.push preserva o estado Zustand (sem reload de página)
      router.push('/dashboard');
    } catch (err) {
      // 422 oauth_only → conta Google sem senha — orienta o usuário
      if (err.status === 422 && err.body?.includes('oauth_only')) {
        setError(
          'Esta conta foi criada com login Google. ' +
          'Use o botão "Entrar com Google" acima para acessar.'
        );
      } else if (err.status === 423) {
        setError('Conta temporariamente bloqueada por excesso de tentativas. Aguarde 15 minutos e tente novamente.');
      } else if (err.status === 429) {
        setError('Muitas tentativas em sequência. Aguarde um momento e tente novamente.');
      } else if (err.status === 403) {
        setError('Acesso bloqueado para este IP. Contate o administrador do sistema.');
      } else {
        setError('E-mail ou senha incorretos. Verifique e tente novamente.');
      }
    } finally {
      setLoading(false);
    }
  }

  async function onMfaVerify(e) {
    e.preventDefault();
    setError('');
    setMfaLoading(true);
    try {
      const resp = await api.post('/api/auth/mfa-verify', {
        challengeToken: mfaChallenge,
        code: mfaCode,
      });
      setSession(resp.user, resp.accessToken);
      router.push('/dashboard');
    } catch {
      setError('Código inválido ou expirado. Tente novamente.');
      setMfaCode('');
    } finally {
      setMfaLoading(false);
    }
  }

  return (
    <>
      <Header />
      <main className="container-page py-12">
        <FadeIn>
          <div className="mx-auto max-w-md">
            <h1 className="mb-1 text-center font-display text-2xl font-bold text-gray-900">
              Entrar no CVFacil.NG
            </h1>

            {mfaChallenge ? (
              <>
                <p className="mb-8 text-center text-sm text-gray-500">
                  Digite o código de 6 dígitos do seu aplicativo autenticador.
                </p>
                <form onSubmit={onMfaVerify} className="space-y-4" noValidate>
                  <div>
                    <label htmlFor="mfaCode" className="mb-1 block text-sm font-medium text-gray-700">
                      Código de verificação
                    </label>
                    <input
                      id="mfaCode"
                      inputMode="numeric"
                      pattern="\d{6}"
                      maxLength={6}
                      autoFocus
                      required
                      value={mfaCode}
                      onChange={(e) => setMfaCode(e.target.value.replace(/\D/g, '').slice(0, 6))}
                      className="w-full rounded-md border border-gray-300 px-3 py-2 text-center text-lg tracking-[0.5em] focus:border-brand-500 focus:outline-none focus:ring-2 focus:ring-brand-500"
                    />
                  </div>
                  {error && (
                    <div role="alert" className="rounded-md bg-red-50 px-3 py-2 text-sm text-red-700">
                      {error}
                    </div>
                  )}
                  <button
                    type="submit"
                    disabled={mfaLoading || mfaCode.length !== 6}
                    className="btn-primary w-full py-3 disabled:opacity-60"
                  >
                    {mfaLoading ? 'Verificando...' : 'Verificar'}
                  </button>
                  <button
                    type="button"
                    onClick={() => { setMfaChallenge(''); setMfaCode(''); setError(''); }}
                    className="w-full text-center text-sm text-gray-500 hover:underline"
                  >
                    Voltar
                  </button>
                </form>
              </>
            ) : (
              <>
            <p className="mb-8 text-center text-sm text-gray-500">
              Use sua conta Google ou seu e-mail e senha.
            </p>

            <a
              href={`${apiBase}/oauth2/authorization/google`}
              className="btn-secondary w-full justify-center py-3"
            >
              <GoogleIcon /> <span className="ml-2">Entrar ou criar conta com Google</span>
            </a>
            {/* O backend já cria a conta automaticamente no primeiro login com Google
                (ver OAuth2LoginSuccessHandler) — este texto só deixa isso visível para
                quem ainda não tem conta, sem precisar de um segundo botão redundante. */}
            <p className="mt-2 text-center text-xs text-gray-400">
              Novo por aqui? O mesmo botão já cria sua conta automaticamente.
            </p>

            {/* Mock OAuth — exibido em dev quando o backend redireciona com ?mock_oauth=true */}
            {mockOauth && (
              <div className="mt-4 rounded-md border border-amber-200 bg-amber-50 p-4">
                <p className="mb-3 text-sm font-medium text-amber-800">
                  🔧 Modo dev — Google OAuth simulado
                </p>
                <p className="mb-3 text-xs text-amber-700">
                  Credenciais Google não configuradas. Informe qualquer e-mail para entrar como
                  um usuário simulado.
                </p>
                <form onSubmit={onMockGoogle} className="flex gap-2">
                  <input
                    type="email"
                    placeholder="seu@email.com"
                    value={mockEmail}
                    onChange={(e) => setMockEmail(e.target.value)}
                    required
                    className="flex-1 rounded-md border border-amber-300 px-3 py-2 text-sm focus:border-amber-500 focus:outline-none focus:ring-2 focus:ring-amber-400"
                  />
                  <button
                    type="submit"
                    disabled={mockLoading}
                    className="rounded-md bg-amber-600 px-4 py-2 text-sm font-semibold text-white hover:bg-amber-700 disabled:opacity-60"
                  >
                    {mockLoading ? '...' : 'Entrar'}
                  </button>
                </form>
              </div>
            )}

            <div className="my-6 flex items-center gap-3 text-xs text-gray-400">
              <span className="h-px flex-1 bg-gray-200" />
              ou continue com e-mail
              <span className="h-px flex-1 bg-gray-200" />
            </div>

            <form onSubmit={onSubmit} className="space-y-4" noValidate autoComplete="off">
              <div>
                <label htmlFor="email" className="mb-1 block text-sm font-medium text-gray-700">
                  E-mail
                </label>
                <input
                  id="email"
                  type="email"
                  autoComplete="off"
                  required
                  value={form.email}
                  onChange={(e) => setForm({ ...form, email: e.target.value })}
                  className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none focus:ring-2 focus:ring-brand-500"
                />
              </div>
              <div>
                <label htmlFor="password" className="mb-1 block text-sm font-medium text-gray-700">
                  Senha
                </label>
                <input
                  id="password"
                  type="password"
                  // "new-password" (nao "off") e o unico valor que navegadores Chrome/Edge/Firefox
                  // realmente respeitam para nao oferecer preencher/salvar senha de login existente.
                  autoComplete="new-password"
                  required
                  value={form.password}
                  onChange={(e) => setForm({ ...form, password: e.target.value })}
                  className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none focus:ring-2 focus:ring-brand-500"
                />
              </div>

              {info && (
                <div role="status" className="rounded-md bg-green-50 px-3 py-2 text-sm text-green-700">
                  {info}
                </div>
              )}

              {error && (
                <div role="alert" className="rounded-md bg-red-50 px-3 py-2 text-sm text-red-700">
                  {error}
                </div>
              )}

              <button type="submit" disabled={loading} className="btn-primary w-full py-3 disabled:opacity-60">
                {loading ? 'Entrando...' : 'Entrar'}
              </button>

              <div className="flex items-center justify-between text-sm">
                <Link href="/forgot-password" className="text-brand-700 hover:underline">
                  Esqueci minha senha
                </Link>
                <Link href="/register" className="text-brand-700 hover:underline">
                  Criar conta
                </Link>
              </div>
            </form>
              </>
            )}
          </div>
        </FadeIn>
      </main>
    </>
  );
}

function GoogleIcon() {
  return (
    <svg width="18" height="18" viewBox="0 0 48 48" aria-hidden="true">
      <path
        fill="#FFC107"
        d="M43.6 20.5H42V20H24v8h11.3c-1.6 4.6-6 8-11.3 8-6.6 0-12-5.4-12-12s5.4-12 12-12c3 0 5.8 1.1 7.9 3l5.7-5.7C34 6.1 29.3 4 24 4 12.9 4 4 12.9 4 24s8.9 20 20 20 20-8.9 20-20c0-1.2-.1-2.3-.4-3.5z"
      />
      <path
        fill="#FF3D00"
        d="M6.3 14.7l6.6 4.8C14.6 16.1 19 13 24 13c3 0 5.8 1.1 7.9 3l5.7-5.7C34 6.1 29.3 4 24 4 16.1 4 9.3 8.5 6.3 14.7z"
      />
      <path
        fill="#4CAF50"
        d="M24 44c5.2 0 9.9-2 13.4-5.2l-6.2-5.2C29.3 35.3 26.8 36 24 36c-5.3 0-9.8-3.4-11.3-8l-6.5 5C9.3 39.5 16.1 44 24 44z"
      />
      <path
        fill="#1976D2"
        d="M43.6 20.5H42V20H24v8h11.3c-.8 2.3-2.3 4.3-4.2 5.6l6.2 5.2C41.5 35.3 44 30 44 24c0-1.2-.1-2.3-.4-3.5z"
      />
    </svg>
  );
}
