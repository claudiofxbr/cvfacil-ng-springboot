'use client';

import { useState, useEffect } from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { NextIntlClientProvider } from 'next-intl';
import { useAuthStore } from '@/lib/stores/authStore';
import { useThemeStore } from '@/lib/stores/themeStore';
import { useLocaleStore } from '@/lib/stores/localeStore';
import { ThemeSwitcher } from '@/components/ui/ThemeSwitcher';
import { IdleTimeoutGuard } from '@/components/ui/IdleTimeoutGuard';
import { api } from '@/lib/apiClient';
import ptBR from '@/locales/pt-BR/common.json';
import enUS from '@/locales/en-US/common.json';
import esES from '@/locales/es-ES/common.json';

// Sem roteamento por URL (nenhum segmento [locale] no App Router), então as
// mensagens são carregadas estaticamente e escolhidas em runtime pelo locale
// salvo em localeStore — ver comentário em localeStore.js.
const MESSAGES = { 'pt-BR': ptBR, 'en-US': enUS, 'es-ES': esES };

/**
 * SessionHydrator: tenta restaurar a sessão do usuário usando o cookie httpOnly
 * de refresh token. Roda uma vez na montagem do app (F5, nova aba, etc.).
 * Se o cookie não existir ou estiver expirado, silencia o erro — o usuário
 * simplesmente não fica autenticado e será redirecionado ao login quando necessário.
 */
function SessionHydrator() {
  const setSession  = useAuthStore((s) => s.setSession);
  const setHydrated = useAuthStore((s) => s.setHydrated);

  useEffect(() => {
    async function hydrate() {
      try {
        const resp = await api.post('/api/auth/refresh', {});
        if (resp?.user && resp?.accessToken) {
          setSession(resp.user, resp.accessToken);
        }
      } catch {
        // Sem sessão válida — normal para usuários não logados
      } finally {
        // Sempre sinaliza que a tentativa de hidratação terminou,
        // seja com sucesso ou sem sessão. Isso libera as páginas
        // protegidas para decidirem se redirecionam ou não.
        setHydrated();
      }
    }
    hydrate();
  // setSession e setHydrated são referências estáveis (criadas pelo zustand)
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return null;
}

/**
 * ThemeHydrator: sincroniza o tema persistido no localStorage com o atributo
 * data-theme do <html>. O script inline em layout.js já aplica o tema antes
 * da hidratação; este componente garante que o estado Zustand e o DOM
 * permaneçam sincronizados após a montagem do React.
 */
function ThemeHydrator() {
  const theme    = useThemeStore((s) => s.theme);
  const setTheme = useThemeStore((s) => s.setTheme);

  useEffect(() => {
    // Re-aplica o tema para sincronizar o DOM com o estado Zustand
    // (necessário caso o script inline falhe ou o estado tenha sido limpo).
    if (theme === 'light') {
      document.documentElement.removeAttribute('data-theme');
    } else {
      document.documentElement.setAttribute('data-theme', theme);
    }
  }, [theme]);

  return null;
}

export function Providers({ children }) {
  const [queryClient] = useState(
    () =>
      new QueryClient({
        defaultOptions: {
          queries: {
            staleTime: 60_000,
            refetchOnWindowFocus: false,
            retry: 1,
          },
        },
      }),
  );
  const locale = useLocaleStore((s) => s.locale);

  return (
    <NextIntlClientProvider locale={locale} messages={MESSAGES[locale] || ptBR} timeZone="UTC">
      <QueryClientProvider client={queryClient}>
        <SessionHydrator />
        <ThemeHydrator />
        {children}
        {/* ThemeSwitcher é global — aparece em todas as páginas */}
        <ThemeSwitcher />
        <IdleTimeoutGuard />
      </QueryClientProvider>
    </NextIntlClientProvider>
  );
}
