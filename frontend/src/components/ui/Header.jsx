'use client';

import Link from 'next/link';
import { useState } from 'react';
import { useTranslations } from 'next-intl';
import { Globe, ChevronDown, LogIn } from 'lucide-react';
import { UserMenu } from '@/components/ui/UserMenu';
import { useAuthStore } from '@/lib/stores/authStore';
import { useLocaleStore } from '@/lib/stores/localeStore';

const LOCALES = [
  { code: 'pt-BR', label: 'Português' },
  { code: 'en-US', label: 'English' },
  { code: 'es-ES', label: 'Español' },
];

export function Header() {
  const user = useAuthStore((s) => s.user);
  const [openLang, setOpenLang] = useState(false);
  // Antes: useState local — o seletor mudava um valor exibido na tela sem
  // nenhum efeito real (next-intl nunca era conectado). Agora lê/grava o
  // locale persistido consumido por Providers.jsx.
  const currentLocale = useLocaleStore((s) => s.locale);
  const setCurrentLocale = useLocaleStore((s) => s.setLocale);
  const t = useTranslations('nav');

  return (
    <header className="sticky top-0 z-40 w-full border-b border-gray-200 bg-white/90 backdrop-blur">
      <div className="container-page flex h-16 items-center justify-between">
        <Link href="/" className="flex items-center gap-2 font-display text-xl font-bold text-brand-900">
          <span
            aria-hidden
            className="inline-block h-7 w-7 rounded-md bg-brand-700"
            style={{ background: 'linear-gradient(135deg, #2E5A88, #1F3A5F)' }}
          />
          CVFacil.<span className="text-brand-500">NG</span>
        </Link>

        <nav className="hidden items-center gap-1 md:flex">
          <Link
            href="/dashboard"
            className="rounded-md px-3 py-2 text-sm font-medium text-gray-700 hover:bg-gray-100"
          >
            {t('templates')}
          </Link>
          <Link
            href="/dashboard"
            className="rounded-md px-3 py-2 text-sm font-medium text-gray-700 hover:bg-gray-100"
          >
            {t('dashboard')}
          </Link>
        </nav>

        <div className="flex items-center gap-2">
          <div className="relative">
            <button
              type="button"
              onClick={() => setOpenLang((v) => !v)}
              className="btn-secondary !py-1.5"
              aria-haspopup="menu"
              aria-expanded={openLang}
            >
              <Globe size={16} aria-hidden className="mr-1" />
              {currentLocale}
              <ChevronDown size={14} aria-hidden className="ml-1" />
            </button>
            {openLang && (
              <ul
                role="menu"
                className="absolute right-0 mt-2 w-40 overflow-hidden rounded-md border border-gray-200 bg-white shadow-lg"
              >
                {LOCALES.map((l) => (
                  <li key={l.code} role="menuitem">
                    <button
                      type="button"
                      onClick={() => { setCurrentLocale(l.code); setOpenLang(false); }}
                      className={`block w-full px-4 py-2 text-left text-sm hover:bg-gray-50 ${
                        currentLocale === l.code ? 'font-semibold text-brand-700' : ''
                      }`}
                    >
                      {l.label}
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </div>
          {user ? (
            <UserMenu user={user} />
          ) : (
            <Link href="/login" className="btn-primary !py-1.5">
              <LogIn size={16} aria-hidden className="mr-1" />
              {t('login')}
            </Link>
          )}
        </div>
      </div>
    </header>
  );
}
