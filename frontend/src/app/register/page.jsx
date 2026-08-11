'use client';

import Link from 'next/link';
import { useState } from 'react';
import { z } from 'zod';
import { Header } from '@/components/ui/Header';
import { FadeIn } from '@/components/ui/Motion';
import { api } from '@/lib/apiClient';

const registerSchema = z.object({
  email: z.string().email('E-mail inválido'),
  password: z
    .string()
    .min(10, 'Mínimo 10 caracteres')
    .regex(/[A-Z]/, 'Deve conter pelo menos uma letra maiúscula')
    .regex(/[a-z]/, 'Deve conter pelo menos uma letra minúscula')
    .regex(/\d/, 'Deve conter pelo menos um número')
    .regex(/[^A-Za-z0-9]/, 'Deve conter pelo menos um símbolo (ex: !@#$)'),
  displayName: z.string().min(2, 'Nome deve ter pelo menos 2 caracteres').max(80),
  locale: z.enum(['pt-BR', 'en-US', 'es-ES']).optional(),
});

export default function RegisterPage() {
  const [form, setForm] = useState({
    email: '',
    password: '',
    displayName: '',
    locale: 'pt-BR',
  });
  const [fieldErrors, setFieldErrors] = useState({});
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  function handleChange(e) {
    setForm({ ...form, [e.target.name]: e.target.value });
    setFieldErrors({ ...fieldErrors, [e.target.name]: undefined });
  }

  async function onSubmit(e) {
    e.preventDefault();
    setError('');
    setFieldErrors({});

    const parsed = registerSchema.safeParse(form);
    if (!parsed.success) {
      const errs = {};
      parsed.error.errors.forEach((err) => {
        if (err.path[0]) errs[err.path[0]] = err.message;
      });
      setFieldErrors(errs);
      return;
    }

    setLoading(true);
    try {
      await api.post('/api/auth/register', parsed.data);
      window.location.href = '/login?registered=true';
    } catch (err) {
      if (err.status === 409) {
        setFieldErrors({ email: 'Este e-mail já está cadastrado.' });
      } else {
        setError('Erro ao criar conta. Tente novamente em instantes.');
      }
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
            <h1 className="mb-1 text-center font-display text-2xl font-bold text-gray-900">
              Criar conta no CVFacil.NG
            </h1>
            <p className="mb-8 text-center text-sm text-gray-500">
              Preencha os dados abaixo para começar.
            </p>

            <form onSubmit={onSubmit} className="space-y-4" noValidate>
              <div>
                <label htmlFor="displayName" className="mb-1 block text-sm font-medium text-gray-700">
                  Nome completo
                </label>
                <input
                  id="displayName"
                  name="displayName"
                  type="text"
                  autoComplete="name"
                  required
                  value={form.displayName}
                  onChange={handleChange}
                  className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none focus:ring-2 focus:ring-brand-500"
                />
                {fieldErrors.displayName && (
                  <p className="mt-1 text-xs text-red-600">{fieldErrors.displayName}</p>
                )}
              </div>

              <div>
                <label htmlFor="email" className="mb-1 block text-sm font-medium text-gray-700">
                  E-mail
                </label>
                <input
                  id="email"
                  name="email"
                  type="email"
                  autoComplete="email"
                  required
                  value={form.email}
                  onChange={handleChange}
                  className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none focus:ring-2 focus:ring-brand-500"
                />
                {fieldErrors.email && (
                  <p className="mt-1 text-xs text-red-600">{fieldErrors.email}</p>
                )}
              </div>

              <div>
                <label htmlFor="password" className="mb-1 block text-sm font-medium text-gray-700">
                  Senha
                </label>
                <input
                  id="password"
                  name="password"
                  type="password"
                  autoComplete="new-password"
                  required
                  value={form.password}
                  onChange={handleChange}
                  className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none focus:ring-2 focus:ring-brand-500"
                />
                <p className="mt-1 text-xs text-gray-400">
                  Mínimo 10 caracteres com maiúsculas, minúsculas, números e símbolos.
                </p>
                {fieldErrors.password && (
                  <p className="mt-1 text-xs text-red-600">{fieldErrors.password}</p>
                )}
              </div>

              <div>
                <label htmlFor="locale" className="mb-1 block text-sm font-medium text-gray-700">
                  Idioma preferido
                </label>
                <select
                  id="locale"
                  name="locale"
                  value={form.locale}
                  onChange={handleChange}
                  className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none focus:ring-2 focus:ring-brand-500"
                >
                  <option value="pt-BR">Português (Brasil)</option>
                  <option value="en-US">English (US)</option>
                  <option value="es-ES">Español</option>
                </select>
              </div>

              {error && (
                <div role="alert" className="rounded-md bg-red-50 px-3 py-2 text-sm text-red-700">
                  {error}
                </div>
              )}

              <button
                type="submit"
                disabled={loading}
                className="btn-primary w-full py-3 disabled:opacity-60"
              >
                {loading ? 'Criando conta...' : 'Criar conta'}
              </button>

              <p className="text-center text-sm text-gray-500">
                Já tem conta?{' '}
                <Link href="/login" className="text-brand-700 hover:underline">
                  Entrar
                </Link>
              </p>
            </form>
          </div>
        </FadeIn>
      </main>
    </>
  );
}
