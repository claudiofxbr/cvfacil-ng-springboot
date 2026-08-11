'use client';

import Link from 'next/link';
import { useState } from 'react';
import { z } from 'zod';
import { Header } from '@/components/ui/Header';
import { FadeIn } from '@/components/ui/Motion';
import { api } from '@/lib/apiClient';

const schema = z.object({
  email: z.string().email('Informe um e-mail válido'),
});

export default function ForgotPasswordPage() {
  const [email, setEmail] = useState('');
  const [emailError, setEmailError] = useState('');
  const [sent, setSent] = useState(false);
  const [loading, setLoading] = useState(false);

  async function onSubmit(e) {
    e.preventDefault();
    setEmailError('');

    const parsed = schema.safeParse({ email });
    if (!parsed.success) {
      setEmailError(parsed.error.errors[0]?.message || 'E-mail inválido');
      return;
    }

    setLoading(true);
    try {
      await api.post('/api/auth/forgot-password', { email: parsed.data.email });
    } catch {
      // Silencia erros para não revelar se o e-mail está cadastrado
    } finally {
      setLoading(false);
      setSent(true);
    }
  }

  return (
    <>
      <Header />
      <main className="container-page py-12">
        <FadeIn>
          <div className="mx-auto max-w-md">
            <h1 className="mb-1 text-center font-display text-2xl font-bold text-gray-900">
              Recuperar senha
            </h1>
            <p className="mb-8 text-center text-sm text-gray-500">
              Digite seu e-mail e enviaremos um link para redefinir sua senha.
            </p>

            {process.env.NODE_ENV === 'development' && (
              <div className="mb-6 rounded-md border border-amber-300 bg-amber-50 px-4 py-3 text-xs text-amber-800">
                <strong>Ambiente local:</strong> o envio de e-mail ainda não está configurado (SMTP pendente).
                O link de recuperação <em>não será enviado</em> de verdade.
                Para redefinir a senha em desenvolvimento, crie uma nova conta ou use o login mock do Google.
              </div>
            )}

            {sent ? (
              <div className="rounded-md bg-green-50 px-4 py-6 text-center">
                <p className="text-sm font-medium text-green-800">
                  Se este e-mail estiver cadastrado, você receberá um link de recuperação em breve.
                </p>
                <p className="mt-2 text-xs text-green-700">
                  Verifique também a caixa de spam.
                </p>
                <Link
                  href="/login"
                  className="mt-4 inline-block text-sm text-brand-700 hover:underline"
                >
                  Voltar para o login
                </Link>
              </div>
            ) : (
              <form onSubmit={onSubmit} className="space-y-4" noValidate>
                <div>
                  <label htmlFor="email" className="mb-1 block text-sm font-medium text-gray-700">
                    E-mail
                  </label>
                  <input
                    id="email"
                    type="email"
                    autoComplete="email"
                    required
                    value={email}
                    onChange={(e) => {
                      setEmail(e.target.value);
                      setEmailError('');
                    }}
                    className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none focus:ring-2 focus:ring-brand-500"
                  />
                  {emailError && (
                    <p className="mt-1 text-xs text-red-600">{emailError}</p>
                  )}
                </div>

                <button
                  type="submit"
                  disabled={loading}
                  className="btn-primary w-full py-3 disabled:opacity-60"
                >
                  {loading ? 'Enviando...' : 'Enviar link de recuperação'}
                </button>

                <p className="text-center text-sm text-gray-500">
                  Lembrou a senha?{' '}
                  <Link href="/login" className="text-brand-700 hover:underline">
                    Voltar para o login
                  </Link>
                </p>
              </form>
            )}
          </div>
        </FadeIn>
      </main>
    </>
  );
}
