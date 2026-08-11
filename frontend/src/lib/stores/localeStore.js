import { create } from 'zustand';
import { persist } from 'zustand/middleware';

export const SUPPORTED_LOCALES = ['pt-BR', 'en-US', 'es-ES'];
const DEFAULT_LOCALE = 'pt-BR';

/**
 * Idioma ativo da aplicação. Não há roteamento por URL (sem segmento
 * [locale] no App Router) — a escolha do usuário fica em localStorage e é
 * lida por Providers.jsx para alimentar o NextIntlClientProvider.
 */
export const useLocaleStore = create(
  persist(
    (set) => ({
      locale: DEFAULT_LOCALE,
      setLocale: (locale) =>
        set({ locale: SUPPORTED_LOCALES.includes(locale) ? locale : DEFAULT_LOCALE }),
    }),
    { name: 'cvfacil-locale' }
  )
);
