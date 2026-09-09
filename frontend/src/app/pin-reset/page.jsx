'use client';

import Link from 'next/link';
import { Suspense, useState } from 'react';
import { useSearchParams } from 'next/navigation';
import { Header } from '@/components/ui/Header';
import { FadeIn } from '@/components/ui/Motion';
import { api } from '@/lib/apiClient';

function PinResetForm() {
  const token = useSearchParams().get('token') || '';
  const [status, setStatus] = useState('idle'); // idle | loading | success | invalid

  async function onConfirm() {
    if (!token) {
      setStatus('invalid');
      return;
    }
    setStatus('loading');
    try {
      await api.post('/api/auth/pin/reset', { token });
      setStatus('success');
    } catch {
      setStatus('invalid');
    }
  }

  return (
    <main className="container-page py-12">
      <FadeIn>
        <div className="mx-auto max-w-md text-center">
          <h1 className="mb-1 font-display text-2xl font-bold text-gray-900">
            Redefinir PIN de acesso
          </h1>

          {status === 'success' ? (
            <div className="mt-6 rounded-md bg-green-50 px-4 py-6">
              <p className="text-sm font-medium text-green-800">
                PIN removido com sucesso. No próximo login com Google, você vai criar um PIN novo.
              </p>
              <Link href="/login" className="mt-4 inline-block text-sm text-brand-700 hover:underline">
                Ir para o login
              </Link>
            </div>
          ) : status === 'invalid' ? (
            <div className="mt-6 rounded-md bg-red-50 px-4 py-6">
              <p className="text-sm font-medium text-red-800">
                Este link é inválido, expirou ou já foi usado.
              </p>
              <Link href="/login" className="mt-4 inline-block text-sm text-brand-700 hover:underline">
                Voltar para o login
              </Link>
            </div>
          ) : (
            <>
              <p className="mb-8 text-sm text-gray-500">
                Confirme para remover o PIN atual desta conta — no próximo login com Google, você
                cria um PIN novo antes de continuar.
              </p>
              <button
                onClick={onConfirm}
                disabled={status === 'loading'}
                className="btn-primary py-3 px-6 disabled:opacity-60"
              >
                {status === 'loading' ? 'Confirmando...' : 'Confirmar redefinição'}
              </button>
            </>
          )}
        </div>
      </FadeIn>
    </main>
  );
}

export default function PinResetPage() {
  return (
    <>
      <Header />
      <Suspense fallback={null}>
        <PinResetForm />
      </Suspense>
    </>
  );
}
