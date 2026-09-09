'use client';

import Link from 'next/link';
import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { Header } from '@/components/ui/Header';
import { FadeIn } from '@/components/ui/Motion';
import { api } from '@/lib/apiClient';
import { useAuthStore } from '@/lib/stores/authStore';

// BUG DE SEGURANÇA CORRIGIDO: o backend não emite mais sessão direto no callback do Google —
// essa página é o passo obrigatório entre "Google autenticou" e "sessão liberada", pedindo um
// PIN de 8 dígitos próprio do CVFacil.NG (nunca do Google). Sem isso, qualquer pessoa com a
// sessão Google do dono já ativa no navegador entrava direto na conta.
export default function OAuth2PinPage() {
  const router = useRouter();
  const setSession = useAuthStore((s) => s.setSession);
  const [mode, setMode] = useState(null); // "setup" | "verify" | "none" | null (carregando)
  const [pin, setPin] = useState('');
  const [pinConfirm, setPinConfirm] = useState('');
  const [error, setError] = useState('');
  const [remainingAttempts, setRemainingAttempts] = useState(null);
  const [loading, setLoading] = useState(false);
  const [forgotSent, setForgotSent] = useState(false);

  useEffect(() => {
    api
      .get('/api/auth/pin/status')
      .then((resp) => setMode(resp.mode))
      .catch(() => setMode('none'));
  }, []);

  function onlyDigits(v) {
    return v.replace(/\D/g, '').slice(0, 8);
  }

  async function onSubmitSetup(e) {
    e.preventDefault();
    setError('');
    if (pin.length !== 8) {
      setError('O PIN deve ter exatamente 8 dígitos.');
      return;
    }
    if (pin !== pinConfirm) {
      setError('Os PINs não coincidem.');
      return;
    }
    setLoading(true);
    try {
      const resp = await api.post('/api/auth/pin/setup', { pin, pinConfirm });
      setSession(resp.user, resp.accessToken);
      router.push('/dashboard');
    } catch {
      setError('Não foi possível criar o PIN. Faça login novamente.');
    } finally {
      setLoading(false);
    }
  }

  async function onSubmitVerify(e) {
    e.preventDefault();
    setError('');
    if (pin.length !== 8) {
      setError('Digite os 8 dígitos do PIN.');
      return;
    }
    setLoading(true);
    try {
      const resp = await api.post('/api/auth/pin/verify', { pin });
      setSession(resp.user, resp.accessToken);
      router.push('/dashboard');
    } catch (err) {
      setPin('');
      if (err.status === 423) {
        setError('Conta temporariamente bloqueada por excesso de tentativas. Aguarde 15 minutos.');
        setRemainingAttempts(0);
      } else {
        let remaining = null;
        try {
          remaining = JSON.parse(err.body || '{}').remainingAttempts;
        } catch {
          // corpo não é JSON válido — segue sem o detalhe de tentativas restantes
        }
        setRemainingAttempts(typeof remaining === 'number' ? remaining : null);
        setError('PIN incorreto.');
      }
    } finally {
      setLoading(false);
    }
  }

  async function onForgotPin() {
    setError('');
    setLoading(true);
    try {
      await api.post('/api/auth/pin/forgot-pending', {});
      setForgotSent(true);
    } catch {
      setForgotSent(true); // resposta sempre otimista — não revela se a conta existe
    } finally {
      setLoading(false);
    }
  }

  return (
    <>
      <Header />
      <main className="container-page py-12">
        <FadeIn>
          <div className="mx-auto max-w-md">
            {mode === null && (
              <p className="text-center text-sm text-gray-500">Carregando...</p>
            )}

            {mode === 'none' && (
              <div className="text-center">
                <h1 className="mb-2 font-display text-2xl font-bold text-gray-900">
                  Sessão expirada
                </h1>
                <p className="mb-6 text-sm text-gray-500">
                  Essa etapa expira em 5 minutos por segurança. Faça login com o Google novamente.
                </p>
                <Link href="/login" className="btn-primary inline-block py-3 px-6">
                  Voltar para o login
                </Link>
              </div>
            )}

            {mode === 'setup' && (
              <>
                <h1 className="mb-1 text-center font-display text-2xl font-bold text-gray-900">
                  Crie seu PIN de acesso
                </h1>
                <p className="mb-8 text-center text-sm text-gray-500">
                  Um PIN de 8 dígitos próprio do CVFacil.NG, exigido em todo login com Google — mesmo
                  com a sessão do Google já ativa, protege sua conta em computadores/navegadores
                  compartilhados.
                </p>
                <form onSubmit={onSubmitSetup} className="space-y-4" noValidate>
                  <div>
                    <label className="mb-1 block text-sm font-medium text-gray-700">
                      PIN (8 dígitos)
                    </label>
                    <input
                      inputMode="numeric"
                      autoFocus
                      required
                      value={pin}
                      onChange={(e) => setPin(onlyDigits(e.target.value))}
                      className="w-full rounded-md border border-gray-300 px-3 py-2 text-center text-lg tracking-[0.3em] focus:border-brand-500 focus:outline-none focus:ring-2 focus:ring-brand-500"
                    />
                  </div>
                  <div>
                    <label className="mb-1 block text-sm font-medium text-gray-700">
                      Confirme o PIN
                    </label>
                    <input
                      inputMode="numeric"
                      required
                      value={pinConfirm}
                      onChange={(e) => setPinConfirm(onlyDigits(e.target.value))}
                      className="w-full rounded-md border border-gray-300 px-3 py-2 text-center text-lg tracking-[0.3em] focus:border-brand-500 focus:outline-none focus:ring-2 focus:ring-brand-500"
                    />
                  </div>
                  {error && (
                    <div role="alert" className="rounded-md bg-red-50 px-3 py-2 text-sm text-red-700">
                      {error}
                    </div>
                  )}
                  <button
                    type="submit"
                    disabled={loading || pin.length !== 8 || pinConfirm.length !== 8}
                    className="btn-primary w-full py-3 disabled:opacity-60"
                  >
                    {loading ? 'Criando...' : 'Criar PIN e continuar'}
                  </button>
                </form>
              </>
            )}

            {mode === 'verify' && (
              <>
                <h1 className="mb-1 text-center font-display text-2xl font-bold text-gray-900">
                  Digite seu PIN
                </h1>
                <p className="mb-8 text-center text-sm text-gray-500">
                  Por segurança, todo login com Google exige o PIN de 8 dígitos desta conta.
                </p>
                <form onSubmit={onSubmitVerify} className="space-y-4" noValidate>
                  <div>
                    <input
                      inputMode="numeric"
                      autoFocus
                      required
                      value={pin}
                      onChange={(e) => setPin(onlyDigits(e.target.value))}
                      className="w-full rounded-md border border-gray-300 px-3 py-2 text-center text-lg tracking-[0.3em] focus:border-brand-500 focus:outline-none focus:ring-2 focus:ring-brand-500"
                    />
                  </div>
                  {error && (
                    <div role="alert" className="rounded-md bg-red-50 px-3 py-2 text-sm text-red-700">
                      {error}
                      {typeof remainingAttempts === 'number' && remainingAttempts > 0 && (
                        <> ({remainingAttempts} tentativa{remainingAttempts === 1 ? '' : 's'} restante
                        {remainingAttempts === 1 ? '' : 's'})</>
                      )}
                    </div>
                  )}
                  <button
                    type="submit"
                    disabled={loading || pin.length !== 8}
                    className="btn-primary w-full py-3 disabled:opacity-60"
                  >
                    {loading ? 'Verificando...' : 'Confirmar'}
                  </button>
                  {forgotSent ? (
                    <p className="text-center text-xs text-green-600">
                      Se essa conta tiver um PIN cadastrado, enviamos um e-mail com instruções.
                    </p>
                  ) : (
                    <button
                      type="button"
                      onClick={onForgotPin}
                      disabled={loading}
                      className="w-full text-center text-sm text-gray-500 hover:underline"
                    >
                      Esqueci meu PIN
                    </button>
                  )}
                </form>
              </>
            )}
          </div>
        </FadeIn>
      </main>
    </>
  );
}
