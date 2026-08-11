'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import Link from 'next/link';
import { useTranslations } from 'next-intl';
import { FileText, LogOut } from 'lucide-react';
import { useAuthStore } from '@/lib/stores/authStore';

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

export function UserMenu({ user }) {
  const [open, setOpen] = useState(false);
  const router = useRouter();
  const clear = useAuthStore((s) => s.clearSession);
  const t = useTranslations('nav');
  // O backend retorna o campo `displayName` (não `name`)
  const label = user.displayName || user.email || 'Usuário';
  const hue = hashToHue(user.email || label);

  function handleLogout() {
    clear();
    router.push('/login');
  }

  return (
    <div className="relative">
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        className="flex h-10 w-10 items-center justify-center overflow-hidden rounded-full border border-gray-300 focus:outline-none focus:ring-2 focus:ring-brand-500 focus:ring-offset-2"
        aria-haspopup="menu"
        aria-expanded={open}
        aria-label="Abrir menu do usuário"
      >
        {user.photoUrl ? (
          // eslint-disable-next-line @next/next/no-img-element
          <img src={user.photoUrl} alt="" className="h-full w-full object-cover" />
        ) : (
          <span
            className="flex h-full w-full items-center justify-center text-sm font-semibold text-white"
            style={{ background: `hsl(${hue}, 55%, 45%)` }}
          >
            {initialsFrom(label)}
          </span>
        )}
      </button>

      {open && (
        <ul
          role="menu"
          className="absolute right-0 mt-2 w-56 overflow-hidden rounded-md border border-gray-200 bg-white shadow-lg"
        >
          <li role="menuitem" className="border-b px-4 py-3 text-sm">
            <div className="truncate font-semibold text-gray-900">{label}</div>
            {user.email && <div className="truncate text-xs text-gray-500">{user.email}</div>}
          </li>
          {/* /dashboard/profile e /dashboard/settings ainda não existem —
              os links foram removidos até essas páginas serem implementadas
              (apontavam para 404). */}
          <MenuLink href="/dashboard" icon={FileText} label={t('myResumes')} />
          <li role="menuitem">
            <button
              type="button"
              onClick={handleLogout}
              className="flex w-full items-center gap-2 px-4 py-2 text-sm text-red-600 hover:bg-red-50"
            >
              <LogOut size={16} aria-hidden /> {t('logout')}
            </button>
          </li>
        </ul>
      )}
    </div>
  );
}

function MenuLink({ href, icon: Icon, label }) {
  return (
    <li role="menuitem">
      <Link
        href={href}
        className="flex items-center gap-2 px-4 py-2 text-sm text-gray-700 hover:bg-gray-50"
      >
        <Icon size={16} aria-hidden /> {label}
      </Link>
    </li>
  );
}
