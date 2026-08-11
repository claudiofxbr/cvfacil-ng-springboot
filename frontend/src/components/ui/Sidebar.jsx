'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { usePathname, useRouter } from 'next/navigation';
import {
  FileText,
  ShieldCheck,
  Users,
  LogOut,
  Globe,
  ChevronDown,
  LogIn,
  Coins,
} from 'lucide-react';
import { useAuthStore } from '@/lib/stores/authStore';
import { useLocaleStore } from '@/lib/stores/localeStore';
import { api } from '@/lib/apiClient';

const LOCALES = [
  { code: 'pt-BR', label: 'Português' },
  { code: 'en-US', label: 'English' },
  { code: 'es-ES', label: 'Español' },
];

const NAV_ITEMS = [
  { href: '/dashboard', label: 'Meus currículos', icon: FileText, exact: true },
  { href: '/dashboard/credits', label: 'Créditos', icon: Coins },
  { href: '/dashboard/security', label: 'Segurança', icon: ShieldCheck },
];

const ADMIN_ITEM = { href: '/dashboard/admin', label: 'Painel admin', icon: Users };

function initialsFrom(nameOrEmail = '') {
  return nameOrEmail
    .split(/[\s@._-]+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((p) => p[0]?.toUpperCase() || '')
    .join('');
}

function hashToHue(str = '') {
  let h = 0;
  for (let i = 0; i < str.length; i++) h = (h * 31 + str.charCodeAt(i)) >>> 0;
  return h % 360;
}

/**
 * Navegação lateral esquerda do dashboard — substitui o Header horizontal
 * dentro de /dashboard/**. Mantém o Header original apenas nas páginas
 * públicas (landing, login, registro), fora deste layout.
 */
export function Sidebar() {
  const pathname = usePathname();
  const router = useRouter();
  const user = useAuthStore((s) => s.user);
  const clear = useAuthStore((s) => s.clearSession);
  const currentLocale = useLocaleStore((s) => s.locale);
  const setCurrentLocale = useLocaleStore((s) => s.setLocale);
  const [openLang, setOpenLang] = useState(false);
  const [balance, setBalance] = useState(null);
  const [unlimited, setUnlimited] = useState(false);

  useEffect(() => {
    if (!user) { setBalance(null); setUnlimited(false); return; }
    api.get('/api/credits/wallet')
      .then((w) => { setBalance(w.balance); setUnlimited(w.unlimited); })
      .catch(() => { setBalance(null); setUnlimited(false); });
  }, [user]);

  const isAdmin = user?.role === 'ADMIN' || user?.role === 'ROOT_MASTER';
  const items = isAdmin ? [...NAV_ITEMS, ADMIN_ITEM] : NAV_ITEMS;

  function handleLogout() {
    clear();
    router.push('/login');
  }

  function isActive(href, exact) {
    return exact ? pathname === href : pathname.startsWith(href);
  }

  return (
    <aside className="flex h-screen w-56 shrink-0 flex-col border-r border-gray-200 bg-white">
      <Link
        href="/"
        className="flex items-center gap-2 border-b border-gray-200 px-4 py-4 font-display text-lg font-bold text-brand-900"
      >
        <span
          aria-hidden
          className="inline-block h-6 w-6 rounded-md"
          style={{ background: 'linear-gradient(135deg, #2E5A88, #1F3A5F)' }}
        />
        CVFacil.<span className="text-brand-500">NG</span>
      </Link>

      <nav className="flex flex-1 flex-col gap-0.5 overflow-y-auto p-2">
        {items.map(({ href, label, icon: Icon, exact }) => (
          <Link
            key={href}
            href={href}
            className={`flex items-center gap-2.5 rounded-md px-3 py-2 text-sm font-medium transition ${
              isActive(href, exact)
                ? 'bg-brand-50 text-brand-700'
                : 'text-gray-600 hover:bg-gray-100 hover:text-gray-900'
            }`}
          >
            <Icon size={16} aria-hidden />
            {label}
            {href === '/dashboard/credits' && balance !== null && (
              <span
                className="ml-auto rounded-full bg-gray-100 px-2 py-0.5 text-xs font-semibold text-gray-600"
                title={unlimited ? 'Créditos ilimitados (Admin/Root)' : undefined}
              >
                {unlimited ? '∞' : balance}
              </span>
            )}
          </Link>
        ))}
      </nav>

      <div className="border-t border-gray-200 p-2">
        <div className="relative mb-1">
          <button
            type="button"
            onClick={() => setOpenLang((v) => !v)}
            className="flex w-full items-center gap-2 rounded-md px-3 py-2 text-xs font-medium text-gray-500 hover:bg-gray-100"
            aria-haspopup="menu"
            aria-expanded={openLang}
          >
            <Globe size={14} aria-hidden />
            {currentLocale}
            <ChevronDown size={12} aria-hidden className="ml-auto" />
          </button>
          {openLang && (
            <ul
              role="menu"
              className="absolute bottom-full left-0 mb-1 w-40 overflow-hidden rounded-md border border-gray-200 bg-white shadow-lg"
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
          <div className="flex items-center gap-2 rounded-md px-2 py-1.5">
            <span
              className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full text-xs font-semibold text-white"
              style={{ background: `hsl(${hashToHue(user.email || user.displayName)}, 55%, 45%)` }}
            >
              {initialsFrom(user.displayName || user.email)}
            </span>
            <div className="min-w-0 flex-1">
              <p className="truncate text-xs font-semibold text-gray-800">
                {user.displayName || user.email}
              </p>
              <p className="truncate text-[10px] text-gray-400">{user.role}</p>
            </div>
            <button
              type="button"
              onClick={handleLogout}
              title="Sair"
              className="shrink-0 rounded-md p-1.5 text-gray-400 hover:bg-red-50 hover:text-red-600"
            >
              <LogOut size={15} aria-hidden />
            </button>
          </div>
        ) : (
          <Link href="/login" className="btn-primary flex w-full items-center justify-center gap-1.5 !py-1.5 text-sm">
            <LogIn size={14} aria-hidden />
            Entrar
          </Link>
        )}
      </div>
    </aside>
  );
}
