'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import { useRouter } from 'next/navigation';
import { useAuthStore } from '@/lib/stores/authStore';
import { api } from '@/lib/apiClient';

// PRD: sessão fecha após 5min de inatividade, com aviso prévio; o usuário só
// volta ao trabalho digitando a senha (sem perder o que estava editando).
const IDLE_LIMIT_MS = 5 * 60 * 1000;
const WARNING_WINDOW_MS = 30 * 1000;

export function IdleTimeoutGuard() {
  const user = useAuthStore((s) => s.user);
  const clearSession = useAuthStore((s) => s.clearSession);
  const router = useRouter();

  const [warning, setWarning] = useState(false);
  const [secondsLeft, setSecondsLeft] = useState(30);
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [verifying, setVerifying] = useState(false);

  // Refs para os timers e para o estado de warning lido dentro dos listeners
  // de atividade — evita closures obsoletas sem precisar re-registrar os
  // listeners a cada render (ver comentário em resetActivity).
  const warnTimer = useRef(null);
  const logoutTimer = useRef(null);
  const countdownInterval = useRef(null);
  const warningRef = useRef(false);

  useEffect(() => {
    warningRef.current = warning;
  }, [warning]);

  const clearTimers = useCallback(() => {
    clearTimeout(warnTimer.current);
    clearTimeout(logoutTimer.current);
    clearInterval(countdownInterval.current);
  }, []);

  const doLogout = useCallback(() => {
    clearTimers();
    clearSession();
    router.push('/login');
  }, [clearTimers, clearSession, router]);

  const scheduleTimers = useCallback(() => {
    clearTimers();
    setWarning(false);
    setError('');
    warnTimer.current = setTimeout(() => {
      setWarning(true);
      setSecondsLeft(WARNING_WINDOW_MS / 1000);
      countdownInterval.current = setInterval(() => {
        setSecondsLeft((s) => (s <= 1 ? 0 : s - 1));
      }, 1000);
      logoutTimer.current = setTimeout(doLogout, WARNING_WINDOW_MS);
    }, IDLE_LIMIT_MS - WARNING_WINDOW_MS);
  }, [clearTimers, doLogout]);

  // Função estável (sem depender de `warning`) para não precisar re-registrar
  // os listeners de atividade a cada mudança de estado — lê o valor atual via ref.
  const resetActivity = useCallback(() => {
    if (warningRef.current) return; // aviso já aberto: só a senha reativa a sessão
    scheduleTimers();
  }, [scheduleTimers]);

  useEffect(() => {
    if (!user) {
      clearTimers();
      return;
    }
    scheduleTimers();
    const events = ['mousemove', 'keydown', 'click', 'scroll', 'touchstart'];
    events.forEach((ev) => window.addEventListener(ev, resetActivity));
    return () => {
      events.forEach((ev) => window.removeEventListener(ev, resetActivity));
      clearTimers();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [user]);

  async function handleContinue(e) {
    e.preventDefault();
    setVerifying(true);
    setError('');
    try {
      await api.post('/api/auth/verify-password', { password });
      setPassword('');
      scheduleTimers();
    } catch {
      setError('Senha incorreta. Tente novamente.');
    } finally {
      setVerifying(false);
    }
  }

  if (!user || !warning) return null;

  return (
    <div className="fixed inset-0 z-[100] flex items-center justify-center bg-black/60 backdrop-blur-sm">
      <div className="mx-4 w-full max-w-sm rounded-2xl bg-white p-6 shadow-2xl">
        <h3 className="mb-1 text-base font-bold text-gray-900">Sessão prestes a expirar</h3>
        <p className="mb-4 text-sm text-gray-500">
          Por inatividade, sua sessão será encerrada em <strong>{secondsLeft}s</strong>. Digite sua
          senha para continuar de onde parou.
        </p>
        <form onSubmit={handleContinue} className="space-y-3">
          <input
            type="password"
            autoFocus
            required
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            placeholder="Sua senha"
            className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none focus:ring-2 focus:ring-brand-500"
          />
          {error && <p className="text-xs text-red-600">{error}</p>}
          <div className="flex gap-2">
            <button
              type="submit"
              disabled={verifying || !password}
              className="btn-primary flex-1 py-2 disabled:opacity-60"
            >
              {verifying ? 'Verificando...' : 'Continuar sessão'}
            </button>
            <button
              type="button"
              onClick={doLogout}
              className="rounded-md border border-gray-200 px-4 py-2 text-sm font-semibold text-gray-600 hover:bg-gray-50"
            >
              Sair
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
