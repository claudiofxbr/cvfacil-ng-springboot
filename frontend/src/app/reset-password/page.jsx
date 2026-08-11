'use client';

import Link from 'next/link';
import { Suspense, useState } from 'react';
import { useSearchParams } from 'next/navigation';
import { z } from 'zod';
import { Header } from '@/components/ui/Header';
import { FadeIn } from '@/components/ui/Motion';
import { api } from '@/lib/apiClient';

const schema = z.object({
  password: z
    .string()
    .min(10, 'Mínimo de 10 caracteres')
    .regex(
      /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^A-Za-z0-9]).+$/,
      'Inclua maiúsculas, minúsculas, dígitos e símbolos'
    ),
});

function ResetPasswordForm() {
  const token = useSearchParams().get('token') || '';
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [status, setStatus] = useState('idle'); // idle | loading | success | invalid

  async function onSubmit(e) {
    e.preventDefault();
    setError('');

    if (!token) {
      setError('Link inválido — falta o token de redefinição.');
      return;
    }

    const parsed = schema.safeParse({ password });
    if (!parsed.success) {
      setError(parsed.error.errors[0]?.message || 'Senha inválida');
      return;
    }

    setStatus('loading');
    try {
      await api.post('/api/auth/reset-password', { token, password: parsed.data.password });
      setStatus('success');
    } catch {
      // Token inválido/expirado/já usado — backend responde 400 nesses casos.
      setStatus('invalid');
    }
  }

  return (
    <main className="container-page py-12">
      <FadeIn>
        <div className="mx-auto max-w-md">
          <h1 className="mb-1 text-center font-display text-2xl font-bold text-gray-900">
            Redefinir senha
          </h1>
          <p className="mb-8 text-center text-sm text-gray-500">
            Escolha uma nova senha para sua conta.
          </p>

          {status === 'success' ? (
            <div className="rounded-md bg-green-50 px-4 py-6 text-center">
              <p className="text-sm font-medium text-green-800">
                Senha redefinida com sucesso.
              </p>
              <Link href="/login" className="mt-4 inline-block text-sm text-brand-700 hover:underline">
                Ir para o login
              </Link>
            </div>
          ) : status === 'invalid' ? (
            <div className="rounded-md bg-red-50 px-4 py-6 text-center">
              <p className="text-sm font-medium text-red-800">
                Este link é inválido, expirou ou já foi usado.
              </p>
              <Link href="/forgot-password" className="mt-4 inline-block text-sm text-brand-700 hover:underline">
                Pedir um novo link
              </Link>
            </div>
          ) : (
            <form onSubmit={onSubmit} className="space-y-4" noValidate>
              <div>
                <label htmlFor="password" className="mb-1 block text-sm font-medium text-gray-700">
                  Nova senha
                </label>
                <input
                  id="password"
                  type="password"
                  autoComplete="new-password"
                  required
                  value={password}
                  onChange={(e) => { setPassword(e.target.value); setError(''); }}
                  className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none focus:ring-2 focus:ring-brand-500"
                />
                {error && <p className="mt-1 text-xs text-red-600">{error}</p>}
              </div>

              <button
                type="submit"
                disabled={status === 'loading'}
                className="btn-primary w-full py-3 disabled:opacity-60"
              >
                {status === 'loading' ? 'Salvando...' : 'Redefinir senha'}
              </button>
            </form>
          )}
        </div>
      </FadeIn>
    </main>
  );
}

export default function ResetPasswordPage() {
  return (
    <>
      <Header />
      {/* useSearchParams exige Suspense boundary no App Router */}
      <Suspense fallback={null}>
        <ResetPasswordForm />
      </Suspense>
    </>
  );
}
